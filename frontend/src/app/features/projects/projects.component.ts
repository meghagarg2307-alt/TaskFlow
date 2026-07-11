import { ChangeDetectionStrategy, Component, DestroyRef, OnInit, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Router, RouterLink } from '@angular/router';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { formatHttpError } from '@core/api/http-error.util';
import { ProjectApi } from '@core/api/project.api';
import { Project } from '@core/models/project.models';
import { AuthStore } from '@core/auth/auth.store';
import { ConfirmDialogComponent } from '../../shared/confirm-dialog.component';

@Component({
  selector: 'tf-projects',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink, ConfirmDialogComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="page">
      <header class="page__header">
        <h1>Projects</h1>
        <div class="page__actions">
          <a routerLink="/team" class="tf-btn tf-btn--ghost">Team</a>
          @if (canCreate()) {
            <button class="tf-btn" (click)="creating.set(!creating())">
              {{ creating() ? 'Cancel' : 'New project' }}
            </button>
          }
        </div>
      </header>

      @if (creating()) {
        <form class="project-form tf-card" [formGroup]="form" (ngSubmit)="create()">
          <div class="project-form__row">
            <label class="project-form__field">
              <span>Name</span>
              <input class="tf-input" formControlName="name" placeholder="Mobile app" />
            </label>
            <label class="project-form__field">
              <span>Key (2–10 chars, uppercase)</span>
              <input class="tf-input" formControlName="key" placeholder="MOB" />
            </label>
          </div>
          <label class="project-form__field">
            <span>Description (optional)</span>
            <textarea class="tf-input" rows="2" formControlName="description"></textarea>
          </label>
          <div class="project-form__actions">
            <button class="tf-btn" [disabled]="form.invalid || saving()" type="submit">
              @if (saving()) { <span>Creating…</span> } @else { <span>Create project</span> }
            </button>
          </div>
        </form>
      }

      @if (error()) {
        <p class="page__error">{{ error() }}</p>
      }

      @if (loading()) {
        <p class="tf-muted">Loading…</p>
      } @else if (projects().length === 0) {
        <div class="empty tf-card">
          <p>No projects yet.</p>
          @if (canCreate()) {
            <p class="tf-muted">Create your first project to get started.</p>
          }
        </div>
      } @else {
        <ul class="project-grid">
          @for (p of projects(); track p.id) {
            <li class="project-grid__item tf-card">
              <header class="project-grid__head">
                <a class="project-grid__link" [routerLink]="['/projects', p.id, 'boards']">
                  <span class="project-grid__key">{{ p.key }}</span>
                  <h2 class="project-grid__name">{{ p.name }}</h2>
                </a>
                @if (canDelete()) {
                  <button
                    type="button"
                    class="tf-btn tf-btn--ghost tf-btn--danger"
                    (click)="askDelete(p, $event)">
                    Delete
                  </button>
                }
              </header>
              @if (p.description) {
                <p class="tf-muted project-grid__desc">{{ p.description }}</p>
              }
            </li>
          }
        </ul>
      }
    </section>

    @if (confirmProject(); as p) {
      <tf-confirm-dialog
        title="Move project to trash?"
        [message]="'“' + p.name + '” and all its boards and tasks will move to Trash for 30 days.'"
        warning="You can restore it from Trash before automatic permanent deletion."
        confirmLabel="Move to trash"
        (confirmed)="deleteProject(p)"
        (cancelled)="confirmProject.set(null)" />
    }
  `,
  styles: [`
    .page { padding: var(--space-5); max-width: 1100px; margin: 0 auto; }
    .page__header { display: flex; align-items: center; justify-content: space-between; margin-bottom: var(--space-5); }
    .page__actions { display: flex; gap: var(--space-2); align-items: center; }
    h1 { margin: 0; font-size: var(--font-size-2xl); }
    .project-form { padding: var(--space-4); margin-bottom: var(--space-5); display: grid; gap: var(--space-3); }
    .project-form__row { display: grid; grid-template-columns: 1fr 1fr; gap: var(--space-3); }
    .project-form__field { display: flex; flex-direction: column; gap: var(--space-1); }
    .project-form__actions { display: flex; justify-content: flex-end; }
    .empty { padding: var(--space-6); text-align: center; }
    .project-grid {
      list-style: none; padding: 0; margin: 0;
      display: grid; grid-template-columns: repeat(auto-fill, minmax(280px, 1fr)); gap: var(--space-4);
    }
    .project-grid__item {
      padding: var(--space-4); cursor: pointer;
      transition: transform 120ms ease, box-shadow 120ms ease;
    }
    .project-grid__item:hover { transform: translateY(-2px); box-shadow: var(--shadow-2); }
    .project-grid__head { display: flex; gap: var(--space-3); align-items: center; justify-content: space-between; margin-bottom: var(--space-2); }
    .project-grid__link { display: flex; gap: var(--space-3); align-items: center; flex: 1; color: inherit; text-decoration: none; }
    .project-grid__key {
      font: 600 var(--font-size-xs)/1 monospace;
      padding: var(--space-1) var(--space-2);
      background: var(--color-brand-100); color: var(--color-brand-700);
      border-radius: var(--radius-sm);
    }
    .project-grid__name { margin: 0; font-size: var(--font-size-lg); }
    .project-grid__desc { margin: 0; font-size: var(--font-size-sm); }
    .page__error {
      margin-bottom: var(--space-3); padding: var(--space-2) var(--space-3);
      background: color-mix(in srgb, var(--color-danger), transparent 88%);
      border: 1px solid color-mix(in srgb, var(--color-danger), transparent 70%);
      color: var(--color-danger); border-radius: var(--radius-md); font-size: var(--font-size-sm);
    }
  `],
})
export class ProjectsComponent implements OnInit {
  private readonly api = inject(ProjectApi);
  private readonly fb  = inject(FormBuilder);
  protected readonly auth = inject(AuthStore);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly projects = signal<Project[]>([]);
  protected readonly loading  = signal(true);
  protected readonly creating = signal(false);
  protected readonly saving   = signal(false);
  protected readonly error = signal<string | null>(null);

  protected readonly canCreate = this.auth.canManage;
  protected readonly canDelete = this.auth.canManage;

  protected readonly confirmProject = signal<Project | null>(null);

  protected readonly form = this.fb.nonNullable.group({
    name:        ['', [Validators.required, Validators.minLength(2)]],
    key:         ['', [Validators.required, Validators.pattern(/^[A-Z][A-Z0-9]*$/), Validators.maxLength(10)]],
    description: [''],
  });

  ngOnInit(): void {
    this.reload();
  }

  private reload(): void {
    this.loading.set(true);
    this.error.set(null);
    this.api.list().pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (rows) => { this.projects.set(rows); this.loading.set(false); },
      error: (err: HttpErrorResponse) => {
        this.loading.set(false);
        this.error.set(formatHttpError(err, 'Could not load projects.'));
      },
    });
  }

  create(): void {
    if (this.form.invalid) return;
    this.saving.set(true);
    this.error.set(null);
    this.api.create(this.form.getRawValue()).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (p) => {
        this.projects.update((rows) => [p, ...rows]);
        this.form.reset({ name: '', key: '', description: '' });
        this.creating.set(false);
        this.saving.set(false);
        this.router.navigate(['/projects', p.id, 'boards']);
      },
      error: (err: HttpErrorResponse) => {
        this.saving.set(false);
        this.error.set(formatHttpError(err, 'Could not create project.'));
      },
    });
  }

  protected askDelete(project: Project, event: Event): void {
    event.stopPropagation();
    event.preventDefault();
    this.confirmProject.set(project);
  }

  protected deleteProject(project: Project): void {
    this.confirmProject.set(null);
    this.api.remove(project.id).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: () => this.projects.update((rows) => rows.filter((p) => p.id !== project.id)),
      error: (err: HttpErrorResponse) => this.error.set(formatHttpError(err, 'Could not delete project.')),
    });
  }
}
