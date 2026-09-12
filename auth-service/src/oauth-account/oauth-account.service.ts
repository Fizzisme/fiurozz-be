import {Injectable} from "@nestjs/common";
import {IAccount} from "../account/interfaces/account.interface.js";
import {IOAuthUser} from "./interfaces/oauth-user.interface.js";
import {PrismaService} from "../prisma/prisma.service.js";
import {AccountService} from "../account/account.service.js";
import {uuidv7} from "uuidv7";
import {OutboxEventService} from "../outboxEvent/outbox-event.service.js";


@Injectable()
export class OauthAccountService {

    constructor(
        private readonly prisma: PrismaService,
        private readonly accountService: AccountService,
        private readonly outboxEventService: OutboxEventService
    ) {}

    // Finds or creates an account for an OAuth profile, and links the
    // OAuth identity to it. Called from both Google and GitHub callback
    // flows via AuthService.oauthLogin.
    async loginWithOauth(profile: IOAuthUser) : Promise<IAccount> {
        // Already linked this exact provider identity before — return
        // the linked account directly, skip everything below.
        const oauthAccount = await this.prisma.oauthAccount.findFirst({
            where: {
                provider: profile.provider,
                providerAccountId: profile.providerAccountId,
            },
            include: {
                account: true,
            }
        })

        if(oauthAccount) return oauthAccount.account;

        // Only look up an existing account by email when this profile
        // actually has one. Passing `email: null` to findUnique would
        // match ANY account with a null email (Postgres/Prisma treat
        // NULL as "unknown", not as a comparable value equal to other
        // NULLs in application logic) — silently linking two unrelated
        // no-email OAuth users to the same account. Skipping the lookup
        // entirely when there's no email to match against avoids that.
        let account: IAccount | null = profile.email ? await this.accountService.findAccountByEmail(profile.email)
            : null
        if (!account) {

            const accountId = uuidv7()

            // Create the account and its outbox event atomically: both
            // succeed or both roll back together, so a crash between
            // the two calls can never leave an account created without
            // a corresponding "account.created" event ever being
            // published (the Outbox pattern's core guarantee).
            const [createdAccount] = await this.prisma.$transaction([
                 this.accountService.createAccount({
                    id: accountId,
                    email: profile.email ?? null,
                    fullName: profile.fullName,
                    displayName: profile.fullName,
                    passwordHash: null,
                    emailVerified: !!profile.email,
                }),
                this.outboxEventService.create(
                    accountId,
                    "account.created",
                    {
                        id: accountId,
                        email: profile.email  ?? null,
                        fullName: profile.fullName,
                        displayName: profile.fullName,
                        avatarUrl: profile.avatarUrl,
                    }
                ),
            ]);

            account = createdAccount
        }

        const oauthId = uuidv7()

        // Link this OAuth identity to the account (new or pre-existing).
        // Not part of the transaction above — see note below.
        await this.prisma.oauthAccount.create({
            data: {
                id: oauthId,
                provider: profile.provider,
                providerAccountId: profile.providerAccountId,
                providerEmail: profile.email ?? null,
                accountId: account.id,
            },
        });

        return account;
    }

}