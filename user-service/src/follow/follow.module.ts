import { Module } from '@nestjs/common';
import { FollowController } from './follow.controller.js';
import { FollowService } from './follow.service.js';

@Module({
  controllers: [FollowController],
  providers: [FollowService],
  // UserService reuses loadFollowingSet to stamp isFollowing onto the
  // users list and public profiles.
  exports: [FollowService]
})
export class FollowModule {}
