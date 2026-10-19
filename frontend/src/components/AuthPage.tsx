import { FormEvent, useState } from 'react';
import Icon from './Icon';

export type Credentials = {
  mode: 'login' | 'register';
  username: string;
  password: string;
  email?: string;
  fullName?: string;
};

export default function AuthPage({ busy, error, message, onAuthenticate }: {
  busy: boolean;
  error: string;
  message: string;
  onAuthenticate: (credentials: Credentials) => void;
}) {
  const [mode, setMode] = useState<'login' | 'register'>('login');
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [email, setEmail] = useState('');
  const [fullName, setFullName] = useState('');
  const [showPassword, setShowPassword] = useState(false);

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    onAuthenticate({ mode, username, password, email, fullName });
  }

  return (
    <main className="auth-layout">
      <section className="auth-story">
        <a className="brand auth-brand" href="#">
          <span className="brand-mark"><Icon name="layers" /></span>
          <span>campus<span className="brand-light">desk</span><small>ISSUE MANAGEMENT</small></span>
        </a>
        <div className="story-copy">
          <span className="story-kicker"><span className="online-dot" />SERVICE, WITH CLARITY</span>
          <h1>Campus issues,<br /><em>moving forward.</em></h1>
          <p>One thoughtful place to report, manage, and resolve the things that keep campus life running.</p>
          <div className="story-points">
            <div><span><Icon name="ticket" /></span><p><strong>Every request, accounted for</strong><small>Stay informed from first report through resolution.</small></p></div>
            <div><span><Icon name="activity" /></span><p><strong>Clear ownership and progress</strong><small>The right team can act on the right issue.</small></p></div>
            <div><span><Icon name="shield" /></span><p><strong>Built around your campus</strong><small>Role-aware access keeps requests in the right hands.</small></p></div>
          </div>
        </div>
        <div className="auth-footnote">A calmer way to keep campus services moving.</div>
      </section>

      <section className="auth-side">
        <div className="auth-card">
          <div className="auth-mobile-brand"><span className="brand-mark"><Icon name="layers" /></span>CampusDesk</div>
          <span className="section-kicker">{mode === 'login' ? 'WELCOME BACK' : 'GET STARTED'}</span>
          <h2>{mode === 'login' ? 'Sign in to your workspace' : 'Create your student account'}</h2>
          <p className="auth-subtitle">{mode === 'login' ? 'Use your campus account to continue.' : 'Set up an account to submit and follow your requests.'}</p>
          <div className="auth-switch" role="tablist" aria-label="Authentication method">
            <button type="button" role="tab" aria-selected={mode === 'login'} className={mode === 'login' ? 'selected' : ''} onClick={() => setMode('login')}>Sign in</button>
            <button type="button" role="tab" aria-selected={mode === 'register'} className={mode === 'register' ? 'selected' : ''} onClick={() => setMode('register')}>Create account</button>
          </div>
          <form className="form-stack auth-form" onSubmit={submit}>
            {mode === 'register' && <>
              <label className="field"><span>Full name</span><input autoComplete="name" required minLength={2} maxLength={120} value={fullName} onChange={(event) => setFullName(event.target.value)} placeholder="Your name" /></label>
              <label className="field"><span>Campus email</span><input type="email" autoComplete="email" required maxLength={120} value={email} onChange={(event) => setEmail(event.target.value)} placeholder="you@campus.edu" /></label>
            </>}
            <label className="field"><span>Username</span><input autoComplete="username" required minLength={3} maxLength={80} value={username} onChange={(event) => setUsername(event.target.value)} placeholder="Your username" /></label>
            <label className="field"><span>Password</span>
              <span className="password-wrap"><input type={showPassword ? 'text' : 'password'} autoComplete={mode === 'login' ? 'current-password' : 'new-password'} required minLength={mode === 'register' ? 6 : undefined} maxLength={200} value={password} onChange={(event) => setPassword(event.target.value)} placeholder="Enter your password" />
                <button type="button" className="password-toggle" onClick={() => setShowPassword((visible) => !visible)}>{showPassword ? 'Hide' : 'Show'}</button>
              </span>
              {mode === 'register' && <small className="field-hint">Use at least 6 characters.</small>}
            </label>
            {message && <div className="auth-success" role="status">{message}</div>}
            {error && <div className="auth-error" role="alert"><Icon name="alert" />{error}</div>}
            <button className="button-primary auth-submit" disabled={busy}>{busy ? <><span className="spinner" />Please wait…</> : <>{mode === 'login' ? 'Sign in' : 'Create account'}<Icon name="arrow" /></>}</button>
          </form>
          <div className="auth-privacy"><Icon name="shield" /><span>Your account is protected with role-based access.</span></div>
        </div>
        <p className="auth-legal">CampusDesk <span>·</span> Campus services, made clearer.</p>
      </section>
    </main>
  );
}
