import {
    CallHandler,
    ExecutionContext,
    Injectable,
    NestInterceptor,
} from "@nestjs/common";
import { Observable } from "rxjs";
import { map } from "rxjs/operators";
import {ApiResponse} from "../interfaces/api-response.interface.js";

// Normalizes every successful controller return value into a
// consistent response shape: { success, timestamp, message, data }.
// Mirrors the shape produced by HttpExceptionFilter for error cases,
// so clients always get the same envelope regardless of outcome.
@Injectable()
export class ResponseInterceptor implements NestInterceptor {

    intercept(
        context: ExecutionContext,
        next: CallHandler,
    ): Observable<any> {

        return next.handle().pipe(
            map((response: ApiResponse) => ({
                success: true,
                timestamp: new Date().toISOString(),
                message: response.message ?? "",
                data: response.data ?? null,
            })),
        );
    }
}