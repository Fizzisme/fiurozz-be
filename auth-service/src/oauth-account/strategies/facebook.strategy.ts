import { Injectable } from "@nestjs/common";
import { PassportStrategy } from "@nestjs/passport";
import { Strategy } from "passport-facebook";

@Injectable()
export class FacebookStrategy extends PassportStrategy(
    Strategy,
    "facebook",
) {
    constructor() {
        super({
            clientID: process.env.FACEBOOK_CLIENT_ID!,
            clientSecret: process.env.FACEBOOK_CLIENT_SECRET!,
            callbackURL: process.env.FACEBOOK_CALLBACK_URL!,

            profileFields: [
                "id",
                "displayName",
                "name",
                "email",
                "photos",
            ],
        });
    }

    async validate(
        accessToken: string,
        refreshToken: string,
        profile: any,
    ) {
        const { id, displayName, name, emails, photos } = profile;

        return {
            provider: "facebook",

            providerId: id,

            // Facebook accounts registered via phone number have no email
            // at all -- explicit null (not undefined) so downstream code
            // can reliably check `profile.email === null` rather than
            // guessing whether the field was omitted.
            email: emails?.[0]?.value ?? null,

            displayName,

            fullName: name?.familyName + ' ' + name?.givenName,

            avatarUrl: photos?.[0]?.value ?? null,
        };
    }
}