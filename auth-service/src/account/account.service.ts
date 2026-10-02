import { Injectable } from '@nestjs/common';
import { PrismaService } from '../prisma/prisma.service.js';
import { IAccount } from './interfaces/account.interface.js';

@Injectable()
export class AccountService {
    constructor(private readonly prisma: PrismaService) {}

    async findAccountByEmail(email: string): Promise<IAccount | null> {
        return this.prisma.account.findUnique({
            where: { email },
        });
    }

    async findAccountById(id: string): Promise<IAccount | null> {
        return this.prisma.account.findUnique({
            where: { id },
        });
    }

    // updateMany so a missing account is a plain count of 0 instead of an
    // error -- the caller is an event consumer, where that is not retryable.
    async updateDisplayName(id: string, displayName: string): Promise<number> {
        const result = await this.prisma.account.updateMany({
            where: { id },
            data: { displayName },
        });
        return result.count;
    }

    createAccount(data: Omit<IAccount, 'status' | 'roles' | 'createdAt' | 'updatedAt' | 'lastLoginAt'>) {
        return this.prisma.account.create({
            data: {
                id: data.id,
                email: data.email,
                fullName: data.fullName,
                displayName: data.displayName,
                passwordHash: data.passwordHash,
                emailVerified: data.emailVerified,
            },
        });
    }
}
