import {
    CallHandler,
    ExecutionContext,
    Injectable,
    NestInterceptor,
} from "@nestjs/common";
import { Observable } from "rxjs";
import { map } from "rxjs/operators";
import {ApiResponse} from "../interfaces/api-response.interface.js";

@Injectable()
export class ResponseInterceptor implements NestInterceptor {

    intercept(
        context: ExecutionContext,
        next: CallHandler,
    ): Observable<any> {

        if (context.getType() !== "http") {
            return next.handle();
        }


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