export interface IOAuthUser {
    provider: "google" | "github" | "facebook";
    providerAccountId: string;
    fullName: string;
    email: string;
    avatarUrl?: string;
}