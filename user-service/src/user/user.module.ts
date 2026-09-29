import { Module } from '@nestjs/common';
import { UserController } from './user.controller.js';
import { UserService } from './user.service.js';
import { FollowModule } from '../follow/follow.module.js';
import { ObjectStorageModule } from '../storage/object-storage.module.js';
import { ProfileImageUploadRateLimitGuard } from './guards/profile-image-upload-rate-limit.guard.js';

@Module({
    imports: [FollowModule, ObjectStorageModule],
    controllers: [UserController],
    providers: [UserService, ProfileImageUploadRateLimitGuard],
})
export class UserModule {}
