import { proxyActivities } from '@temporalio/workflow';
import * as activities from './activities';

const {
	helloActivity
} = proxyActivities<typeof activities>({
  startToCloseTimeout: '1 minute',
  retry: {
    backoffCoefficient: 1,
    initialInterval: '3 seconds',
  },
});

export async function agentWorkflow(): Promise<string> {
	return helloActivity();
}