/*
 * Everything the two pages share: where the token lives, how a request carries it, and what the
 * user is told when one fails.
 *
 * The token goes in sessionStorage rather than a cookie, because the API authenticates with an
 * Authorization header and nothing else; that also means no plain link and no <img src> can fetch
 * a file - every read goes through fetch() here.
 */

const TOKEN_KEY = 'skills-keeper.token';
const NOTICE_KEY = 'skills-keeper.notice';

export const LOGIN_PAGE = '/login.html';
export const FILES_PAGE = '/';

export const MESSAGES = {
	usernameRequired: 'Введите имя пользователя',
	passwordRequired: 'Введите пароль',
	invalidCredentials: 'Имя пользователя или пароль неверны',
	badLoginRequest: 'Проверьте имя пользователя и пароль: сервис не принял такие значения',
	loginUnavailable: 'Войти сейчас не получается, попробуйте позже',
	sessionExpired: 'Сеанс закончился, войдите снова',
	listFailed: 'Не удалось загрузить список файлов',
	previewFailed: 'Не удалось получить содержимое файла',
	previewMissing: 'Файла больше нет',
	previewUnsupported: 'Предпросмотр для этого типа файла недоступен, файл можно скачать',
	previewTooLarge: 'Файл слишком велик для предпросмотра, его можно скачать',
	downloadFailed: 'Не удалось скачать файл',
	uploadNoFile: 'Выберите файл',
	uploadRejected: 'Сервис не принял этот файл',
	uploadTooLarge: 'Файл больше допустимого размера',
	uploadFailed: 'Не удалось загрузить файл',
	networkUnavailable: 'Сервис недоступен, попробуйте позже',
	serverError: 'Сервис ответил ошибкой, попробуйте позже',
	emptyList: 'Файлов пока нет',
	nothingSelected: 'Выберите файл слева, чтобы увидеть его содержимое',
};

/** A request the service answered with an error status. */
export class ApiError extends Error {

	constructor(status, message) {
		super(message);
		this.status = status;
	}
}

/**
 * Thrown after the token has been discarded and the browser sent to the login page: a caller has
 * nothing left to report, and catching it is how a page knows to stop quietly.
 */
export class SessionEnded extends Error {
}

/*
 * Storage can throw outright - a browser set to block site data does exactly that - so every
 * access is guarded and a missing token is treated the same as no session.
 */

export function token() {
	try {
		return sessionStorage.getItem(TOKEN_KEY);
	}
	catch (ignored) {
		return null;
	}
}

export function rememberToken(value) {
	try {
		sessionStorage.setItem(TOKEN_KEY, value);
	}
	catch (ignored) {
		// Nothing to do: the page keeps working for as long as it stays loaded.
	}
}

export function forgetToken() {
	try {
		sessionStorage.removeItem(TOKEN_KEY);
	}
	catch (ignored) {
		// Already unreachable, which is what forgetting it was for.
	}
}

/** Carries one short message across the jump to the login page, so it stays out of the address. */
export function takeNotice() {
	try {
		const notice = sessionStorage.getItem(NOTICE_KEY);
		sessionStorage.removeItem(NOTICE_KEY);
		return notice;
	}
	catch (ignored) {
		return null;
	}
}

function leaveNotice(notice) {
	try {
		sessionStorage.setItem(NOTICE_KEY, notice);
	}
	catch (ignored) {
		// The login screen simply appears without an explanation.
	}
}

/** replace(), not assign(): the page being left must not come back with the browser's Back button. */
export function goToLogin(notice) {
	forgetToken();
	if (notice) {
		leaveNotice(notice);
	}
	location.replace(LOGIN_PAGE);
}

/** A plain call with no token: used by the login form, which has none yet. */
export async function send(path, options = {}) {
	try {
		return await fetch(path, options);
	}
	catch (cause) {
		throw new ApiError(0, MESSAGES.networkUnavailable);
	}
}

/**
 * A call that carries the token. A rejected token ends the session here, once, instead of in every
 * caller; every other failure is raised as an {@link ApiError} for the page to show.
 *
 * @param failureMessage what the user is told when the service answers with an error status
 */
export async function request(path, { failureMessage, ...options } = {}) {
	const bearer = token();
	if (!bearer) {
		goToLogin();
		throw new SessionEnded();
	}

	const response = await send(path, {
		...options,
		headers: { ...(options.headers ?? {}), Authorization: `Bearer ${bearer}` },
	});

	if (response.status === 401) {
		goToLogin(MESSAGES.sessionExpired);
		throw new SessionEnded();
	}
	if (!response.ok) {
		throw new ApiError(response.status, failureMessage ?? MESSAGES.serverError);
	}
	return response;
}
