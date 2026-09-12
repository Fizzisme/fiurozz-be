import {Module} from "@nestjs/common";
import {OauthAccountService} from "./oauth-account.service.js";
import {AccountModule} from "../account/account.module.js";
import {GoogleStrategy} from "./strategies/google.strategy.js";
import {PassportModule} from "@nestjs/passport";
import {GithubStrategy} from "./strategies/github.strategy.js";
import {OutboxEventModule} from "../outboxEvent/outbox-event.module.js";
import {FacebookStrategy} from "./strategies/facebook.strategy.js";

@Module({
    imports: [
        OutboxEventModule,
        AccountModule,
        PassportModule.register({
            session: false,
        }),
    ],
    providers: [OauthAccountService, GoogleStrategy, GithubStrategy, FacebookStrategy],
    exports: [OauthAccountService]
})
export class OauthAccountModule{}