import { Routes } from '@angular/router';
import { authGuard } from './guards/auth.guard';

export const routes: Routes = [
  { path: '', redirectTo: 'documents', pathMatch: 'full' },
  {
    path: 'login',
    loadComponent: () => import('./pages/login/login.component').then((m) => m.LoginComponent),
  },
  {
    path: 'register',
    loadComponent: () =>
      import('./pages/register/register.component').then((m) => m.RegisterComponent),
  },
  {
    // Lazy-loaded: this chunk is only downloaded when the route is visited.
    path: 'documents',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./pages/documents/documents.component').then((m) => m.DocumentsComponent),
  },
  {
    path: 'documents/:id',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./pages/document-detail/document-detail.component').then(
        (m) => m.DocumentDetailComponent
      ),
  },
  { path: '**', redirectTo: 'documents' },
];
