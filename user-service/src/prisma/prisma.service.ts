import { Injectable, OnModuleInit, OnModuleDestroy } from "@nestjs/common";
import { PrismaClient } from "../generated/prisma/client.js";
import { PrismaPg } from "@prisma/adapter-pg";

@Injectable()
export class PrismaService extends PrismaClient implements OnModuleInit, OnModuleDestroy {
    constructor() {
        const adapter = new PrismaPg({
            connectionString: process.env.DATABASE_URL as string,
            max: Number(process.env.DB_POOL_MAX ?? 10),
            // Prisma 7 driver adapters inherit node-postgres defaults, where idle
            // sockets close after 10s -- too short for a remote DB, since every
            // reconnect pays DNS + TCP + TLS again.
            idleTimeoutMillis: Number(process.env.DB_POOL_IDLE_TIMEOUT_MS ?? 60_000),
            // node-postgres defaults to 0 (wait forever); Prisma 6 used 5s.
            connectionTimeoutMillis: Number(process.env.DB_POOL_CONNECTION_TIMEOUT_MS ?? 5_000),
            keepAlive: true,
        });
        super({ adapter });
    }

    // Opens the first connection at boot instead of on the first request, so a
    // bad DATABASE_URL fails startup rather than a user-facing call.
    async onModuleInit() {
        await this.$connect();
    }

    // Requires app.enableShutdownHooks() in main.ts to actually fire on
    // SIGTERM/SIGINT -- otherwise Nest never calls this and the pool's
    // sockets get killed abruptly instead of closed cleanly.
    async onModuleDestroy() {
        await this.$disconnect();
    }
}