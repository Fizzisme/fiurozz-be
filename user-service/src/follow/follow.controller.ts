import {Controller, DefaultValuePipe, Delete, Get, Param, ParseIntPipe, Post, Query, Req} from '@nestjs/common';
import type {Request} from 'express';
import {FollowService} from "./follow.service.js";

// Routes here are all two-segment (`:id/follow`, `:id/followers`, ...), so
// they never collide with UserController's single-segment `GET :identifier`
// regardless of module registration order -- Nest/Express match on segment
// count, not declaration order, for structurally distinct patterns.
@Controller()
export class FollowController {

    constructor(private readonly followService: FollowService) {}

    @Post(':id/follow')
    follow(@Req() req: Request, @Param('id') id: string) {
        return this.followService.follow(req, id);
    }

    @Delete(':id/follow')
    unfollow(@Req() req: Request, @Param('id') id: string) {
        return this.followService.unfollow(req, id);
    }

    @Get(':id/followers')
    getFollowers(
        @Param('id') id: string,
        @Query('limit', new DefaultValuePipe(20), ParseIntPipe) limit: number,
        @Query('cursor') cursor?: string,
    ) {
        return this.followService.getFollowers(id, { limit, cursor });
    }

    @Get(':id/following')
    getFollowing(
        @Param('id') id: string,
        @Query('limit', new DefaultValuePipe(20), ParseIntPipe) limit: number,
        @Query('cursor') cursor?: string,
    ) {
        return this.followService.getFollowing(id, { limit, cursor });
    }
}
