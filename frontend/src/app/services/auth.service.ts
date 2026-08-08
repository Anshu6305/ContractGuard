import { Injectable, signal, computed } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, tap } from 'rxjs';
import { AuthResponse } from '../models/models';

const TOKEN_KEY = 'cg_token';
const EMAIL_KEY = 'cg_email';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly api = 'http://localhost:8080/api/auth';

  // Angular signals: the modern alternative to BehaviorSubject for simple state.
  private readonly tokenSignal = signal<string | null>(localStorage.getItem(TOKEN_KEY));
  readonly isLoggedIn = computed(() => this.tokenSignal() !== null);
  readonly email = signal<string | null>(localStorage.getItem(EMAIL_KEY));

  constructor(private http: HttpClient) {}

  register(email: string, password: string, fullName: string): Observable<AuthResponse> {
    return this.http
      .post<AuthResponse>(`${this.api}/register`, { email, password, fullName })
      .pipe(tap((res) => this.store(res)));
  }

  login(email: string, password: string): Observable<AuthResponse> {
    return this.http
      .post<AuthResponse>(`${this.api}/login`, { email, password })
      .pipe(tap((res) => this.store(res)));
  }

  logout(): void {
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(EMAIL_KEY);
    this.tokenSignal.set(null);
    this.email.set(null);
  }

  get token(): string | null {
    return this.tokenSignal();
  }

  private store(res: AuthResponse): void {
    localStorage.setItem(TOKEN_KEY, res.token);
    localStorage.setItem(EMAIL_KEY, res.email);
    this.tokenSignal.set(res.token);
    this.email.set(res.email);
  }
}
