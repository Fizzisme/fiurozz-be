import {Injectable} from "@nestjs/common";
import {PassportStrategy} from "@nestjs/passport";
import {Strategy, Profile} from "passport-github2";
import axios from "axios";

@Injectable()
export class GithubStrategy extends PassportStrategy(
    Strategy,
    'github',
){
    constructor() {
        super({
            clientID: process.env.GITHUB_CLIENT_ID!,
            clientSecret: process.env.GITHUB_CLIENT_SECRET!,
            callbackURL: process.env.GITHUB_CALLBACK_URL!,
            scope: ['user:email'],
        });
    }

    async validate(
        accessToken: string,
        refreshToken: string,
        profile: Profile,
    ) {

        let email =
            profile.emails?.[0]?.value;

        if (!email) {
            const { data } = await axios.get(
                "https://api.github.com/user/emails",
                {
                    headers: {
                        Authorization: `Bearer ${accessToken}`,
                        'User-Agent': 'auth-service',
                    },
                },
            );

            const primary = data.find(
                (e: any) => e.primary && e.verified,
            );

            email = primary?.email;
        }

        return {
            provider: 'github',
            providerAccountId: profile.id,
            email,
            fullName: profile.displayName || profile.username,
            displayName: profile.username,
            avatarUrl: profile.photos?.[0]?.value,
        };
    }
}