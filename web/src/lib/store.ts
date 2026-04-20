import { create } from 'zustand';
import { io, Socket } from 'socket.io-client';

interface User {
  id: string;
  email: string;
  name: string;
  plan: string;
  isAdmin?: boolean;
}

interface LoginUserPayload {
  id: string;
  email: string;
  name?: string;
  plan?: string;
  isAdmin?: boolean;
  is_admin?: boolean;
}

interface AppState {
  user: User | null;
  token: string | null;
  socket: Socket | null;
  login: (user: LoginUserPayload, token: string) => void;
  logout: () => void;
  initSocket: () => void;
  disconnectSocket: () => void;
}

export const useStore = create<AppState>((set, get) => ({
  user: null,
  token: null,
  socket: null,
  
  login: (user, token) => {
    const normalizedUser: User = {
      id: user.id,
      email: user.email,
      name: user.name || user.email.split('@')[0],
      plan: user.plan || 'free',
      isAdmin: Boolean(user.isAdmin ?? user.is_admin),
    };

    localStorage.setItem('token', token);
    set({ user: normalizedUser, token });
    get().initSocket();
  },
  
  logout: () => {
    localStorage.removeItem('token');
    get().disconnectSocket();
    set({ user: null, token: null });
  },

  initSocket: () => {
    const { token, socket } = get();
    if (socket || !token) return;

    const newSocket = io(process.env.NEXT_PUBLIC_SOCKET_URL || '', {
      withCredentials: true,
    });

    newSocket.on('connect', () => {
      newSocket.emit('authenticate', { token });
    });

    set({ socket: newSocket });
  },

  disconnectSocket: () => {
    const { socket } = get();
    if (socket) {
      socket.disconnect();
      set({ socket: null });
    }
  }
}));
