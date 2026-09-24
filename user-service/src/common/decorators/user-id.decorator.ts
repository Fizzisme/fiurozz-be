import { createParamDecorator, ExecutionContext, UnauthorizedException } from '@nestjs/common';
import type { Request } from 'express';

// Reads the caller's identity from X-User-Id, a header the API Gateway
// attaches only after it has already verified the JWT (see root
// CLAUDE.md, identity headers). This service trusts that header
// completely and never re-verifies the token itself.
//
// The UnauthorizedException guard below is defense-in-depth, not the
// actual auth check -- that already happened at the gateway when it
// runs (see user-service/PROBLEMS.md for a config gap here). It should
// be unreachable in practice, since the Gateway's JWTAuth middleware
// guarantees this header is present for any authenticated route.
export const UserId = createParamDecorator((_: unknown, ctx: ExecutionContext): string => {
    const req = ctx.switchToHttp().getRequest<Request>();
    const userId = req.headers['x-user-id'] as string | undefined;

    if (!userId) {
        throw new UnauthorizedException('User not found.');
    }

    return userId;
});

// Same header, but for routes that serve anonymous visitors too (the users
// list and any public profile): the identity is used to personalize the
// response, not to authorize it, so a missing one is not an error.
//
// The gateway proxies /api/users with auth_mode: optional and sets the
// identity headers unconditionally, so an anonymous request arrives with
// X-User-Id as an *empty string* rather than with no header at all. Passing
// that '' down to a @db.Uuid column would fail the cast in Postgres, so it
// has to collapse to undefined here.
export const OptionalUserId = createParamDecorator((_: unknown, ctx: ExecutionContext): string | undefined => {
    const req = ctx.switchToHttp().getRequest<Request>();
    const userId = req.headers['x-user-id'] as string | undefined;

    return userId ? userId : undefined;
});
