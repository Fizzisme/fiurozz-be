import {Injectable} from "@nestjs/common";
import {PassportStrategy} from "@nestjs/passport";
import { Strategy, Profile, VerifyCallback } from "passport-google-oauth20";
import {IOAuthUser} from "../interfaces/oauth-user.interface.js";


@Injectable()
export class GoogleStrategy extends PassportStrategy (
    Strategy,
    "google",
){
    constructor() {
        super({
            clientID: process.env.GOOGLE_CLIENT_ID!,
            clientSecret: process.env.GOOGLE_CLIENT_SECRET!,
            callbackURL: process.env.GOOGLE_CALLBACK_URL!,
            scope: ["email", "profile"],
        });
    }

    async validate(
        accessToken: string,
        refreshToken: string,
        profile: Profile,
        done: VerifyCallback,
    ){

        const user: IOAuthUser = {
            provider: 'google',
            providerAccountId: profile.id,
            email: profile.emails![0].value,
            fullName: profile.displayName,
            avatarUrl: profile.photos?.[0]?.value,
        }


        done(null, user);
    }

}
