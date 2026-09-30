import {
    Body,
    Controller,
    DefaultValuePipe,
    Delete,
    Get,
    Param,
    ParseIntPipe,
    Patch,
    Post,
    Put,
    Query,
    UploadedFile,
    UseGuards,
    UseInterceptors,
} from '@nestjs/common';
import { FileInterceptor } from '@nestjs/platform-express';
import { UserService } from './user.service.js';
import { UpdateProfileDto } from './dto/update-profile.dto.js';
import { OptionalUserId, UserId } from '../common/decorators/user-id.decorator.js';
import { ProfileImageUploadRateLimitGuard } from './guards/profile-image-upload-rate-limit.guard.js';

@Controller()
export class UserController {
    constructor(private readonly userService: UserService) {}

    @Get('me')
    getMe(@UserId() userId: string) {
        return this.userService.getMe(userId);
    }

    @Patch('me')
    updateMe(@UserId() userId: string, @Body() dto: UpdateProfileDto) {
        return this.userService.updateProfile(userId, dto);
    }

    @Post('me/avatar')
    @UseGuards(ProfileImageUploadRateLimitGuard)
    @UseInterceptors(FileInterceptor('file', { limits: { files: 1, fileSize: UserService.MAX_AVATAR_BYTES } }))
    uploadAvatar(@UserId() userId: string, @UploadedFile() file?: Express.Multer.File) {
        return this.userService.uploadAvatar(userId, file);
    }

    @Put('me/avatar')
    @UseGuards(ProfileImageUploadRateLimitGuard)
    @UseInterceptors(FileInterceptor('file', { limits: { files: 1, fileSize: UserService.MAX_AVATAR_BYTES } }))
    replaceAvatar(@UserId() userId: string, @UploadedFile() file?: Express.Multer.File) {
        return this.userService.replaceAvatar(userId, file);
    }

    @Delete('me/avatar')
    deleteAvatar(@UserId() userId: string) {
        return this.userService.deleteAvatar(userId);
    }

    @Post('me/cover')
    @UseGuards(ProfileImageUploadRateLimitGuard)
    @UseInterceptors(FileInterceptor('file', { limits: { files: 1, fileSize: UserService.MAX_COVER_BYTES } }))
    uploadCover(@UserId() userId: string, @UploadedFile() file?: Express.Multer.File) {
        return this.userService.uploadCover(userId, file);
    }

    @Put('me/cover')
    @UseGuards(ProfileImageUploadRateLimitGuard)
    @UseInterceptors(FileInterceptor('file', { limits: { files: 1, fileSize: UserService.MAX_COVER_BYTES } }))
    replaceCover(@UserId() userId: string, @UploadedFile() file?: Express.Multer.File) {
        return this.userService.replaceCover(userId, file);
    }

    @Delete('me/cover')
    deleteCover(@UserId() userId: string) {
        return this.userService.deleteCover(userId);
    }

    // Public route (gateway runs /api/users as auth_mode: optional), so the
    // viewer id is only there to personalize isFollowing, never to authorize.
    @Get()
    getUsers(
        @Query('limit', new DefaultValuePipe(20), ParseIntPipe) limit: number,
        @OptionalUserId() viewerId?: string,
        @Query('cursor') cursor?: string,
        @Query('occupation') occupation?: string,
        @Query('skills') skills?: string,
        @Query('sort') sort?: string,
    ) {
        return this.userService.getUsers(
            {
                limit,
                cursor,
                occupation: occupation
                    ?.split(',')
                    .map((v) => v.trim())
                    .filter(Boolean),
                skills: skills
                    ?.split(',')
                    .map((v) => v.trim())
                    .filter(Boolean),
                sort,
            },
            viewerId,
        );
    }

    // :identifier accepts either a user id (UUID) or a displayName --
    // both are unique, see user.service.ts#getUser for how it tells them apart.
    @Get(':identifier')
    getUser(@Param('identifier') identifier: string, @OptionalUserId() viewerId?: string) {
        return this.userService.getUser(identifier, viewerId);
    }
}
