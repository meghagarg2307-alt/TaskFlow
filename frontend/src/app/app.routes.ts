import { Routes } from '@angular/router';
import { authGuard, roleGuard } from './core/auth/auth.guard';

/**
 * Top-level routes. Feature areas are lazy-loaded via {@code loadComponent} so
 * the initial bundle stays small (the user only pays for what they navigate to).
 */
export const routes: Routes = [
  {
    path: 'login',
    loadComponent: () => import('./features/auth/login.component').then((m) => m.LoginComponent),
    title: 'Sign in · TaskFlow',
  },
  {
    path: 'register',
    loadComponent: () => import('./features/auth/register.component').then((m) => m.RegisterComponent),
    title: 'Create account · TaskFlow',
  },
  {
    path: 'join',
    loadComponent: () =>
      import('./features/auth/accept-invite.component').then((m) => m.AcceptInviteComponent),
    title: 'Join workspace · TaskFlow',
  },
  {
    path: '',
    canActivate: [authGuard],
    loadComponent: () => import('./features/shell/shell.component').then((m) => m.ShellComponent),
    children: [
      {
        path: '',
        pathMatch: 'full',
        redirectTo: 'projects',
      },
      {
        path: 'projects',
        loadComponent: () =>
          import('./features/projects/projects.component').then((m) => m.ProjectsComponent),
        title: 'Projects · TaskFlow',
      },
      {
        path: 'team',
        loadComponent: () =>
          import('./features/team/team.component').then((m) => m.TeamComponent),
        title: 'Team · TaskFlow',
      },
      {
        path: 'trash',
        canActivate: [roleGuard(['ADMIN'])],
        loadComponent: () =>
          import('./features/trash/trash.component').then((m) => m.TrashComponent),
        title: 'Trash · TaskFlow',
      },
      {
        path: 'projects/:projectId/boards',
        loadComponent: () =>
          import('./features/boards/boards.component').then((m) => m.BoardsComponent),
        title: 'Boards · TaskFlow',
      },
      {
        path: 'boards/:boardId',
        // Kanban component is built in Step 7; placeholder for now keeps routing working.
        loadComponent: () =>
          import('./features/kanban/kanban.component').then((m) => m.KanbanComponent),
        title: 'Board · TaskFlow',
      },
    ],
  },
  { path: 'forbidden', loadComponent: () => import('./features/auth/forbidden.component').then((m) => m.ForbiddenComponent) },
  { path: '**', redirectTo: '' },
];
