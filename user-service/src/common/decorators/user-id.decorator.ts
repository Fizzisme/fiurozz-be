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
