import {
    ArgumentsHost,
    Catch,
    ExceptionFilter,
    HttpException,
    HttpStatus,
} from "@nestjs/common";
import { Request, Response } from "express";

@Catch()
export class HttpExceptionFilter
    implements ExceptionFilter
{
    catch(
        exception: unknown,
        host: ArgumentsHost,
    ) {

        if (host.getType() !== "http") {
            return;
        }


        const ctx = host.switchToHttp();

        const response = ctx.getResponse<Response>();
        const request = ctx.getRequest<Request>();

        let status = HttpStatus.INTERNAL_SERVER_ERROR;

        let message = "Internal server error.";

        if (exception instanceof HttpException) {

            status = exception.getStatus();

            const error = exception.getResponse();

            if (typeof error === "string") {
                message = error;
            }

            else if (
                typeof error === "object" &&
                error !== null
            ) {

                const body = error as Record<string, unknown>;

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