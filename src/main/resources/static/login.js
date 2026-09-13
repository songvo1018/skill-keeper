import { ApiError, FILES_PAGE, MESSAGES, rememberToken, send, takeNotice } from '/api.js';

const form = document.getElementById('login-form');
const usernameField = document.getElementById('username');
const passwordField = document.getElementById('password');
const usernameError = document.getElementById('username-error');
const passwordError = document.getElementById('password-error');
const formError = document.getElementById('form-error');
const submitButton = document.getElementById('submit');

let inFlight = false;

/** Whatever ended the previous session - an expired token, most often - is explained here. */
const notice = takeNotice();
if (notice) {
	formError.textContent = notice;
}

function validate(username, password) {
	usernameError.textContent = username ? '' : MESSAGES.usernameRequired;
	passwordError.textContent = password ? '' : MESSAGES.passwordRequired;
	return Boolean(username && password);
}

/**
 * A rejected login is answered in the same words whether the name exists or not, because the
 * service deliberately answers both the same way and the page must not add a distinction of its
 * own. The password field is cleared; the name is left so it need not be retyped.
 */
function reportFailure(error) {
	if (error instanceof ApiError && error.status === 401) {
		formError.textContent = MESSAGES.invalidCredentials;
	}
	else if (error instanceof ApiError && error.status === 400) {
		formError.textContent = MESSAGES.badLoginRequest;
	}
	else if (error instanceof ApiError && error.status === 0) {
		formError.textContent = MESSAGES.networkUnavailable;
	}
	else {
		formError.textContent = MESSAGES.loginUnavailable;
	}
	passwordField.value = '';
}

form.addEventListener('submit', async (event) => {
	// The form is never submitted the browser's way: that would put the password in a request this
	// page cannot add a token to, and could put it in the address bar.
	event.preventDefault();

	if (inFlight) {
		return;
	}

	const username = usernameField.value.trim();
	const password = passwordField.value;
	formError.textContent = '';
	if (!validate(username, password)) {
		return;
	}

	inFlight = true;
	submitButton.disabled = true;
	try {
		const response = await send('/api/auth/login', {
			method: 'POST',
			headers: { 'Content-Type': 'application/json' },
			body: JSON.stringify({ username, password }),
		});
		if (!response.ok) {
			throw new ApiError(response.status, MESSAGES.loginUnavailable);
		}

		const body = await response.json();
		rememberToken(body.token);
		location.replace(FILES_PAGE);
	}
	catch (error) {
		reportFailure(error);
	}
	finally {
		inFlight = false;
		submitButton.disabled = false;
	}
});
