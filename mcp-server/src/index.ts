import express, { Request, Response } from 'express';
import { McpServer } from '@modelcontextprotocol/sdk/server/mcp.js';
import { StreamableHTTPServerTransport } from '@modelcontextprotocol/sdk/server/streamableHttp.js';
import { z } from 'zod';
import { authMiddleware, McpContext } from './auth';
import { handleWellKnown } from './wellKnown';
import * as client from './client';

const PORT = parseInt(process.env.PORT ?? '3000', 10);
const BASE_URL = (process.env.PUBLIC_BASE_URL ?? 'https://pingpongg.site').replace(/\/$/, '');

const app = express();
app.use(express.json());

// ── 전역 요청 로거 ──
app.use((req, _res, next) => {
  const hasAuth = !!req.headers['authorization'];
  const authSummary = hasAuth
    ? `Bearer (present, length=${req.headers['authorization']!.length - 7})`
    : 'none';
  console.log(`[REQ] ${new Date().toISOString()} ${req.method} ${req.path}`);
  console.log(`  Authorization: ${authSummary}`);
  console.log(`  Content-Type: ${req.headers['content-type'] ?? 'none'}`);
  if (req.body && Object.keys(req.body).length > 0) {
    const bodyStr = JSON.stringify(req.body);
    console.log(`  Body: ${bodyStr.length > 300 ? bodyStr.substring(0, 300) + '...' : bodyStr}`);
  }
  next();
});

// OAuth well-known metadata (proxied from mcp-server so nginx can route /.well-known/ here)
app.get('/.well-known/oauth-authorization-server', handleWellKnown);

// OAuth Protected Resource Metadata (RFC 9728 / MCP OAuth 2025 spec)
app.get('/.well-known/oauth-protected-resource', (_req: Request, res: Response) => {
  res.json({
    resource: `${BASE_URL}/mcp`,
    authorization_servers: [`${BASE_URL}`],
    bearer_methods_supported: ['header'],
  });
});

// Health check
app.get('/health', (_req: Request, res: Response) => {
  res.json({ status: 'ok' });
});

// MCP endpoint — one McpServer instance per request (stateless)
app.all(
  '/mcp',
  authMiddleware as express.RequestHandler,
  async (req: Request & { mcpContext?: McpContext }, res: Response) => {
    const ctx = req.mcpContext!;
    console.log(`[MCP] Handler entered - email: ${ctx.email}, teamId: ${ctx.teamId}`);

    const server = new McpServer({
      name: 'pingpong-mcp',
      version: '1.0.0',
    });

    const srv = server as any;

    // Tool 1: get_qa_failures
    srv.tool(
      'get_qa_failures',
      '현재 팀의 QA 실패 케이스와 최신 실행 결과를 조회합니다.',
      {},
      async () => {
        try {
          const data = await client.getQaFailures(ctx.teamId);
          return { content: [{ type: 'text', text: JSON.stringify(data, null, 2) }] };
        } catch (err: unknown) {
          const message = err instanceof Error ? err.message : String(err);
          return { content: [{ type: 'text', text: `오류가 발생했습니다: ${message}` }], isError: true };
        }
      }
    );

    // Tool 2: get_tasks
    srv.tool(
      'get_tasks',
      '현재 팀의 Task 목록을 조회합니다. status 파라미터로 필터링 가능합니다.',
      { status: z.string().optional().describe('Task 상태 필터 (예: Backend, Frontend, Done)') },
      async (args: { status?: string }) => {
        try {
          const data = await client.getTasks(ctx.teamId, args.status);
          return { content: [{ type: 'text', text: JSON.stringify(data, null, 2) }] };
        } catch (err: unknown) {
          const message = err instanceof Error ? err.message : String(err);
          return { content: [{ type: 'text', text: `오류가 발생했습니다: ${message}` }], isError: true };
        }
      }
    );

    // Tool 3: get_task_details
    srv.tool(
      'get_task_details',
      'Task ID로 Task 상세 정보(flows, 연결된 endpoints, 요구사항)를 조회합니다.',
      { task_id: z.string().describe('Task의 Notion Page ID') },
      async (args: { task_id: string }) => {
        try {
          const data = await client.getTaskDetails(args.task_id);
          return { content: [{ type: 'text', text: JSON.stringify(data, null, 2) }] };
        } catch (err: unknown) {
          const message = err instanceof Error ? err.message : String(err);
          return { content: [{ type: 'text', text: `오류가 발생했습니다: ${message}` }], isError: true };
        }
      }
    );

    // Tool 4: get_api_schema
    srv.tool(
      'get_api_schema',
      'Endpoint ID로 API 엔드포인트의 상세 스키마(parameters, requestBody, responses)를 조회합니다.',
      { endpoint_id: z.number().describe('Endpoint ID') },
      async (args: { endpoint_id: number }) => {
        try {
          const data = await client.getApiSchema(args.endpoint_id);
          return { content: [{ type: 'text', text: JSON.stringify(data, null, 2) }] };
        } catch (err: unknown) {
          const message = err instanceof Error ? err.message : String(err);
          return { content: [{ type: 'text', text: `오류가 발생했습니다: ${message}` }], isError: true };
        }
      }
    );

    const transport = new StreamableHTTPServerTransport({
      sessionIdGenerator: undefined,
    });

    res.on('close', () => {
      console.log(`[MCP] Connection closed - email: ${ctx.email}, teamId: ${ctx.teamId}`);
      transport.close();
      server.close();
    });

    try {
      await server.connect(transport);
      console.log(`[MCP] server.connect() succeeded`);
      await transport.handleRequest(req, res, req.body);
      console.log(`[MCP] transport.handleRequest() completed`);
    } catch (err: unknown) {
      const message = err instanceof Error ? err.message : String(err);
      const stack = err instanceof Error ? err.stack : undefined;
      console.error(`[MCP] Error during handleRequest: ${message}`);
      if (stack) console.error(stack);
    }
  }
);

// ── 전역 에러 핸들러 ──
app.use((err: unknown, req: Request, res: Response, _next: express.NextFunction) => {
  const message = err instanceof Error ? err.message : String(err);
  const stack = err instanceof Error ? err.stack : undefined;
  console.error(`[ERROR] Unhandled error on ${req.method} ${req.path}: ${message}`);
  if (stack) console.error(stack);
  res.status(500).json({ error: 'internal_server_error', error_description: message });
});

process.on('unhandledRejection', (reason) => {
  console.error('[UNHANDLED REJECTION]', reason);
});

process.on('uncaughtException', (err) => {
  console.error('[UNCAUGHT EXCEPTION]', err.message, err.stack);
});

app.listen(PORT, () => {
  console.log(`PingPong MCP Server running on port ${PORT}`);
});
