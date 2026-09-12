import {
    ArgumentsHost,
    Catch,
    ExceptionFilter,
    HttpException,
    HttpStatus,
} from "@nestjs/common";
import { Request, Response } from "express";


// Catches every unhandled exception (both NestJS HttpException and
// raw JS errors) and normalizes them into the same response shape
// as ResponseInterceptor uses for success responses — so clients
// always get { success, timestamp, message, data } regardless of
// outcome.
@Catch()
export class HttpExceptionFilter
    implements ExceptionFilter
{
    catch(
        exception: unknown,
        host: ArgumentsHost,
    ) {

        const ctx = host.switchToHttp();

        const response = ctx.getResponse<Response>();
        const request = ctx.getRequest<Request>();

        // Default to 500 + generic message for anything that isn't a
        // recognized HttpException (e.g. a raw thrown Error, a bug),
        // so internal error details never leak to the client.
        let status = HttpStatus.INTERNAL_SERVER_ERROR;

        let message = "Internal server error.";

        if (exception instanceof HttpException) {

            status = exception.getStatus();

            const error = exception.getResponse();

            // e.g. throw new UnauthorizedException('Invalid email or password.')
            // — getResponse() returns the string directly.
            if (typeof error === "string") {
                message = error;
            }

            else if (
                typeof error === "object" &&
                error !== null
            ) {

                const body = error as Record<string, unknown>;

                // ValidationPipe errors: message is an array of
                // validation failures (one per invalid field). Only
                // the first is surfaced here — the rest are dropped.
                if (Array.isArray(body.message)) {
                    message = body.message[0];
                }

                else if (typeof body.message === "string") {
                    message = body.message;
                }
            }
        }

        response.status(status).json({
            success: false,
            timestamp: new Date().toISOString(),
            message,
            data: null,
        });
    }
}