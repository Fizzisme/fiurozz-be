import { Module } from '@nestjs/common';
import { AuthController } from './auth.controller.js';
import { AuthService } from './auth.service.js';
import {JwtModule} from "@nestjs/jwt";
import {JwtTokenService} from "./jwt/jwt-token.service.js";
import {PasswordService} from "./password/password.service.js";
import {RefreshTokenService} from "./token/refresh-token.service.js";
import {TokenService} from "./token/token.service.js";
import {AccountModule} from "../account/account.module.js";
import {OauthAccountModule} from "../oauth-account/oauth-account.module.js";
import {OutboxEventModule} from "../outboxEvent/outbox-event.module.js";


@Module({
    imports: [
        JwtModule.register({}),
        AccountModule,
        OauthAccountModule,
        OutboxEventModule
    ],
    controllers:[AuthController],
    providers: [
        AuthService,
        JwtTokenService,
        PasswordService,
        RefreshTokenService,
        TokenService,
    ]
})
export class AuthModule {}
