import axios from 'axios';

const SPRING_BASE_URL = process.env.SPRING_BASE_URL ?? 'http://localhost:8080';

export const springClient = axios.create({
  baseURL: SPRING_BASE_URL,
  timeout: 30000,
  headers: { 'Content-Type': 'application/json' },
});

export async function getQaFailures(teamId: number) {
  const response = await springClient.get('/api/v1/qa/failures', {
    params: { teamId },
  });
  return response.data;
}

export async function getTasks(teamId: number, status?: string) {
  const params: Record<string, unknown> = {};
  if (status) params['status'] = status;
  const response = await springClient.get(`/api/v1/tasks/teams/${teamId}`, { params });
  return response.data;
}

export async function getTaskDetails(taskId: string) {
  const response = await springClient.get(`/api/v1/tasks/${taskId}/details`);
  return response.data;
}

export async function getApiSchema(endpointId: number) {
  const response = await springClient.get(`/api/v1/endpoints/${endpointId}/schema`);
  return response.data;
}
