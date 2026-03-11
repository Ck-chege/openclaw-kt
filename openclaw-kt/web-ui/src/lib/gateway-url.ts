const FALLBACK_GATEWAY_URL = 'ws://127.0.0.1:18789/';

function browserWebSocketProtocol() {
  return window.location.protocol === 'https:' ? 'wss:' : 'ws:';
}

export function getDefaultGatewayUrl(): string {
  if (typeof window === 'undefined') {
    return FALLBACK_GATEWAY_URL;
  }

  if (import.meta.env.DEV) {
    return `${browserWebSocketProtocol()}//${window.location.host}/gw`;
  }

  return `${browserWebSocketProtocol()}//${window.location.host}/`;
}

export function resolveGatewayUrl(explicitUrl?: string): string {
  const trimmedExplicit = explicitUrl?.trim();
  if (trimmedExplicit) {
    return trimmedExplicit;
  }

  if (typeof window !== 'undefined') {
    const storedUrl = localStorage.getItem('openclaw-gateway-url')?.trim();
    if (storedUrl) {
      return storedUrl;
    }
  }

  return getDefaultGatewayUrl();
}
