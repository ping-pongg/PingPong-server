import { Request, Response } from 'express';

const BASE_URL = (process.env.PUBLIC_BASE_URL ?? 'https://pingpongg.site').replace(/\/$/, '');

export function handleWellKnown(_req: Request, res: Response): void {
  res.json({
    issuer: `${BASE_URL}/`,
    authorization_endpoint: `${BASE_URL}/oauth/authorize`,
    token_endpoint: `${BASE_URL}/oauth/token`,
    response_types_supported: ['code'],
    grant_types_supported: ['authorization_code', 'refresh_token'],
    code_challenge_methods_supported: ['S256'],
  });
}
