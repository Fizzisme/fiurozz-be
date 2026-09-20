import { ArrayMaxSize, IsArray, IsDateString, IsEnum, IsOptional, IsString, IsUrl, MaxLength } from 'class-validator';
import { Gender, Occupation } from '../../generated/prisma/enums.js';

export class UpdateProfileDto {
    @IsOptional()
    @IsString()
    @MaxLength(150)
    fullName?: string;

    @IsOptional()
    @IsUrl()
    avatarUrl?: string;

    @IsOptional()
    @IsUrl()
    coverUrl?: string;

    @IsOptional()
    @IsString()
    @MaxLength(500)
    bio?: string;

    @IsOptional()
    @IsEnum(Occupation)
    occupation?: Occupation;

    @IsOptional()
    @IsString()
    @MaxLength(100)
    company?: string;

    @IsOptional()
    @IsString()
    @MaxLength(100)
    location?: string;

    @IsOptional()
    @IsDateString()
    birthday?: string;

    @IsOptional()
    @IsUrl()
    website?: string;

    @IsOptional()
    @IsEnum(Gender)
    gender?: Gender;

    @IsOptional()
    @IsString()
    @MaxLength(10)
    language?: string;

    @IsOptional()
    @IsString()
    @MaxLength(50)
    timezone?: string;

    // Full replacement of the caller's skill list, not a diff/append --
    // sending [] clears all skills. Names are matched/stored case-
    // insensitively (lowercased) so "NextJS" and "nextjs" are one skill.
    @IsOptional()
    @IsArray()
    @ArrayMaxSize(20)
    @IsString({ each: true })
    @MaxLength(50, { each: true })
    skills?: string[];
}
