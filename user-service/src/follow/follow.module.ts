import { Module } from '@nestjs/common';
import { FollowController } from './follow.controller.js';
import { FollowService } from './follow.service.js';

@Module({
  controllers: [FollowController],
  providers: [FollowService]
})
export class FollowModule {}
