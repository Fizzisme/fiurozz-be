import {IsDate, IsDateString, IsEmail, IsEnum, IsNotEmpty, IsString, MaxLength, MinLength} from 'class-validator';


export class RegisterDto {
    @IsEmail()
    email: string;

    @IsString()
    @MinLength(3)
    @MaxLength(255)
    fullName: string;

    @IsString()
    @MinLength(3)
    @MaxLength(255)
    displayName: string;

    @IsString()
    @MinLength(8)
    @MaxLength(255)
    password: string;

    @IsNotEmpty()
    @IsString()
    country: string;

    @IsEnum(['MALE', 'FEMALE','UNKNOWN'])
    gender: string;

    @IsNotEmpty()
    @IsDateString()
    birthday: Date;
}