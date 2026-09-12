import './tracing.js'
import { NestFactory } from '@nestjs/core';
import { AppModule } from './app.module.js';
import {ResponseInterceptor} from "./common/interceptors/response.interceptor.js";
import {HttpExceptionFilter} from "./common/filters/http-exception.filter.js";
import {ValidationPipe} from "@nestjs/common";

async function bootstrap() {
  const app = await NestFactory.create(AppModule);

  app.useGlobalPipes(new ValidationPipe(
      {
        whitelist: true,
        forbidNonWhitelisted: true,
        transform: true,
      }
  ));

  app.useGlobalInterceptors(
      new ResponseInterceptor(),
  );

  app.useGlobalFilters(
      new HttpExceptionFilter(),
  );



  await app.listen(process.env.PORT ?? 3000);
}
bootstrap();
