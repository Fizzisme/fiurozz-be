import {Controller, Get, Req} from '@nestjs/common';
import {UserService} from "./user.service.js";
import type {Request} from 'express';

@Controller()
export class UserController {

    constructor(private readonly userService: UserService) {}

    @Get('me')
    getMe(@Req() req: Request){

        return this.userService.getMe(req);
    }
}
