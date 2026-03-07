import { Request, Response, NextFunction } from 'express';
import { jwtVerify } from 'jose';

const JWT_SECRET = process.env.JWT_SECRET;
if (!JWT_SECRET) {
  throw new Error('[MCP Server] JWT_SECRET environment variable is required');
}

export interface McpContext {
  email: string;
  teamId: number;
}

function getSecretKey(): Uint8Array {
  return Buffer.from(JWT_SECRET!, 'base64');
}

export async function verifyMcpToken(token: string): Promise<McpContext> {
  const key = getSecretKey();
  const { payload } = await jwtVerify(token, key, { algorithms: ['HS256'] });

  const email = payload['email'] as string;
  const teamId = Number(payload['teamId']);
  const type = payload['type'] as string;

  if (!email || !teamId || type !== 'mcp') {
    throw new Error('Invalid MCP token claims');
  }

  return { email, teamId };
}

export async function authMiddleware(
  req: Request & { mcpContext?: McpContext },
  res: Response,
  next: NextFunction
): Promise<void> {
  const authorization = req.headers['authorization'];
  if (!authorization || !authorization.startsWith('Bearer ')) {
    res.status(401).json({
      error: 'unauthorized',
      error_description: 'Missing or invalid Authorization header',
    });
    return;
  }

  const token = authorization.substring(7);
  try {
    req.mcpContext = await verifyMcpToken(token);
    next();
  } catch {
    res.status(401).json({
      error: 'unauthorized',
      error_description: 'Invalid or expired token',
    });
  }
}
