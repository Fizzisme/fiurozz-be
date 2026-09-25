import { Injectable } from '@nestjs/common';
import { compare, hash } from 'bcrypt';

@Injectable()
export class PasswordService {
    async hashPassword(password: string) {
        return hash(password, 10);
    }

    async comparePassword(password: string, hash: string) {
        return compare(password, hash);
    }
}
