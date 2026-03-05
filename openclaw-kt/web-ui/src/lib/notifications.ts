import { toast } from 'sonner';

export const notify = {
  success(message: string) {
    toast.success(message);
  },

  error(message: string) {
    toast.error(message);
  },

  warning(message: string) {
    toast.warning(message);
  },

  info(message: string) {
    toast.info(message);
  },

  connected() {
    toast.success('Connected to gateway');
  },

  disconnected() {
    toast.error('Disconnected from gateway');
  },

  connecting() {
    toast.info('Connecting to gateway...');
  },

  rpcError(method: string, error: string) {
    toast.error(`${method}: ${error}`);
  },

  agentComplete(sessionKey?: string) {
    toast.success(sessionKey ? `Agent finished (${sessionKey})` : 'Agent finished');
  },

  sessionCreated(key: string) {
    toast.success(`Session created: ${key}`);
  },

  sessionDeleted(key: string) {
    toast.info(`Session deleted: ${key}`);
  },

  sessionReset(key: string) {
    toast.info(`Session reset: ${key}`);
  },
};
