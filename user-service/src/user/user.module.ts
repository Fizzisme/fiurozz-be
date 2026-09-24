import { Module } from '@nestjs/common';
import { UserController } from './user.controller.js';
import { UserService } from './user.service.js';
import { FollowModule } from '../follow/follow.module.js';

@Module({
  imports: [FollowModule],
  controllers: [UserController],
  providers: [UserService]
})
export class UserModule {}
