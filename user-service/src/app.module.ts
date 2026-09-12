import { Module } from '@nestjs/common';
import { UserModule } from './user/user.module.js';
import { PrismaModule } from './prisma/prisma.module.js';
import {ConfigModule} from "@nestjs/config";
import {ConsumerModule} from "./consumer/consumer.module.js";
@Module({
  imports: [
    ConfigModule.forRoot({
      isGlobal: true,
      envFilePath: '.env',
    }),
    UserModule,
    PrismaModule,
    ConsumerModule
  ],
})
export class AppModule {}
