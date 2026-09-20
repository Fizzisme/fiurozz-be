import {Body, Controller, DefaultValuePipe, Get, Param, ParseIntPipe, Patch, Query, Req} from '@nestjs/common';
import {UserService} from "./user.service.js";
import {UpdateProfileDto} from "./dto/update-profile.dto.js";
import type {Request} from 'express';

@Controller()
export class UserController {

    constructor(private readonly userService: UserService) {}

    @Get('me')
    getMe(@Req() req: Request){
        return this.userService.getMe(req);
    }

    @Patch('me')
    updateMe(@Req() req: Request, @Body() dto: UpdateProfileDto){
        return this.userService.updateProfile(req, dto);
    }

    @Get()
    getUsers(
        @Query('limit', new DefaultValuePipe(20), ParseIntPipe) limit: number,
        @Query('cursor') cursor?: string,
        @Query('occupation') occupation?: string,
        @Query('skills') skills?: string,
        @Query('sort') sort?: string,
    ){
        return this.userService.getUsers({
            limit,
            cursor,
            occupation: occupation?.split(',').map((v) => v.trim()).filter(Boolean),
            skills: skills?.split(',').map((v) => v.trim()).filter(Boolean),
            sort,
        });
    }

    // :identifier accepts either a user id (UUID) or a displayName --
    // both are unique, see user.service.ts#getUser for how it tells them apart.
    @Get(':identifier')
    getUser(@Param('identifier') identifier: string){
        return this.userService.getUser(identifier);
    }
}
