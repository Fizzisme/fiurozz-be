import {Controller, DefaultValuePipe, Delete, Get, Param, ParseIntPipe, Post, Query} from '@nestjs/common';
import {FollowService} from "./follow.service.js";
import {OptionalUserId, UserId} from "../common/decorators/user-id.decorator.js";

// Routes here are all two-segment (`:id/follow`, `:id/followers`, ...), so
// they never collide with UserController's single-segment `GET :identifier`
// regardless of module registration order -- Nest/Express match on segment
// count, not declaration order, for structurally distinct patterns.
@Controller()
export class FollowController {

    constructor(private readonly followService: FollowService) {}

    @Post(':id/follow')
    follow(@UserId() userId: string, @Param('id') id: string) {
        return this.followService.follow(userId, id);
    }

    @Delete(':id/follow')
    unfollow(@UserId() userId: string, @Param('id') id: string) {
        return this.followService.unfollow(userId, id);
    }

    @Get(':id/followers')
    getFollowers(
        @Param('id') id: string,
        @Query('limit', new DefaultValuePipe(20), ParseIntPipe) limit: number,
        @OptionalUserId() viewerId?: string,
        @Query('cursor') cursor?: string,
    ) {
        return this.followService.getFollowers(id, { limit, cursor }, viewerId);
    }

    @Get(':id/following')
    getFollowing(
        @Param('id') id: string,
        @Query('limit', new DefaultValuePipe(20), ParseIntPipe) limit: number,
        @OptionalUserId() viewerId?: string,
        @Query('cursor') cursor?: string,
    ) {
        return this.followService.getFollowing(id, { limit, cursor }, viewerId);
    }
}
