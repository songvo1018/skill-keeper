import { ApiError, MESSAGES, SessionEnded, goToLogin, request, token } from '/api.js';

/**
 * Above this the content is not fetched at all: the point of the preview is a glance at the file,
 * and pulling ten megabytes into the tab to render it is not that. The download button stays.
 */
const PREVIEW_LIMIT_BYTES = 1024 * 1024;

const SIZE_UNITS = ['Б', 'КБ', 'МБ', 'ГБ'];

const uploadForm = document.getElementById('upload-form');
const fileInput = document.getElementById('file-input');
const uploadButton = document.getElementById('upload-button');
const uploadError = document.getElementById('upload-error');
const listStatus = document.getElementById('list-status');
const retryButton = document.getElementById('retry');
const filesTable = document.getElementById('files-table');
const filesBody = document.getElementById('files-body');
const previewTitle = document.getElementById('preview-title');
const previewStatus = document.getElementById('preview-status');
const previewBody = document.getElementById('preview-body');
const logoutButton = document.getElementById('logout');

/** The object URL the preview is showing, if any; released as soon as it is replaced. */
let previewObjectUrl = null;

/** Grows with every preview request, so a slow answer cannot overwrite a newer one. */
let previewRequest = 0;

let uploading = false;

function isText(contentType) {
	return contentType.startsWith('text/')
		|| contentType === 'application/json'
		|| contentType === 'application/xml'
		|| contentType.endsWith('+xml')
		|| contentType.endsWith('+json');
}

function formatSize(bytes) {
	let size = bytes;
	let unit = 0;
	while (size >= 1024 && unit < SIZE_UNITS.length - 1) {
		size /= 1024;
		unit += 1;
	}
	const rounded = unit === 0 ? size : Math.round(size * 10) / 10;
	return `${rounded} ${SIZE_UNITS[unit]}`;
}

function cell(row, text) {
	const td = document.createElement('td');
	td.textContent = text;
	row.append(td);
	return td;
}

function releasePreviewObjectUrl() {
	if (previewObjectUrl) {
		URL.revokeObjectURL(previewObjectUrl);
		previewObjectUrl = null;
	}
}

function clearPreview() {
	releasePreviewObjectUrl();
	previewBody.replaceChildren();
	previewTitle.textContent = 'Содержимое';
	previewStatus.textContent = MESSAGES.nothingSelected;
}

function clearList() {
	filesBody.replaceChildren();
	filesTable.hidden = true;
}

/**
 * The page is never shown with somebody's data and no token: on first load, and again when the
 * browser restores the page from its cache after a logout, which does not re-run the script.
 */
function requireSession() {
	if (token()) {
		return true;
	}
	clearList();
	clearPreview();
	goToLogin();
	return false;
}

// --- Listing ---

function renderList(files) {
	clearList();

	if (files.length === 0) {
		listStatus.textContent = MESSAGES.emptyList;
		return;
	}

	for (const file of files) {
		const row = document.createElement('tr');
		// textContent everywhere: a filename is data, and a file named like markup must read as its
		// name rather than become part of the page.
		cell(row, file.originalFilename).classList.add('filename');
		cell(row, file.contentType ?? '—');
		cell(row, formatSize(file.size));

		const actions = document.createElement('td');
		actions.classList.add('actions');

		const previewButton = document.createElement('button');
		previewButton.type = 'button';
		previewButton.textContent = 'Показать';
		previewButton.addEventListener('click', () => showPreview(file));

		const downloadButton = document.createElement('button');
		downloadButton.type = 'button';
		downloadButton.textContent = 'Скачать';
		downloadButton.addEventListener('click', () => download(file));

		actions.append(previewButton, ' ', downloadButton);
		row.append(actions);
		filesBody.append(row);
	}

	listStatus.textContent = '';
	filesTable.hidden = false;
}

async function loadList() {
	if (!requireSession()) {
		return;
	}

	retryButton.hidden = true;
	listStatus.textContent = 'Загружаю список…';
	try {
		const response = await request('/api/files', { failureMessage: MESSAGES.listFailed });
		renderList(await response.json());
	}
	catch (error) {
		if (error instanceof SessionEnded) {
			return;
		}
		clearList();
		listStatus.textContent = error instanceof ApiError ? error.message : MESSAGES.listFailed;
		retryButton.hidden = false;
	}
}

// --- Preview ---

async function showPreview(file) {
	if (!requireSession()) {
		return;
	}

	const thisRequest = ++previewRequest;
	releasePreviewObjectUrl();
	previewBody.replaceChildren();
	previewTitle.textContent = file.originalFilename;

	const contentType = file.contentType ?? '';
	if (file.size > PREVIEW_LIMIT_BYTES) {
		previewStatus.textContent = MESSAGES.previewTooLarge;
		return;
	}
	if (!isText(contentType) && !contentType.startsWith('image/')) {
		previewStatus.textContent = MESSAGES.previewUnsupported;
		return;
	}

	previewStatus.textContent = 'Загружаю содержимое…';
	try {
		const response = await request(`/api/files/${encodeURIComponent(file.id)}`, {
			failureMessage: MESSAGES.previewFailed,
		});
		const blob = await response.blob();
		if (thisRequest !== previewRequest) {
			return;
		}

		if (isText(contentType)) {
			// As text, into textContent: the bytes are shown, never interpreted as part of the page.
			const pre = document.createElement('pre');
			pre.textContent = await blob.text();
			previewBody.replaceChildren(pre);
		}
		else {
			// An object URL in <img>, never in an iframe or a new tab: an image cannot script, but a
			// document opened from a blob URL would run in this page's own origin.
			previewObjectUrl = URL.createObjectURL(blob);
			const image = document.createElement('img');
			image.src = previewObjectUrl;
			image.alt = file.originalFilename;
			previewBody.replaceChildren(image);
		}
		previewStatus.textContent = '';
	}
	catch (error) {
		if (error instanceof SessionEnded || thisRequest !== previewRequest) {
			return;
		}
		previewBody.replaceChildren();
		previewStatus.textContent = notFound(error) ? MESSAGES.previewMissing : MESSAGES.previewFailed;
	}
}

function notFound(error) {
	return error instanceof ApiError && error.status === 404;
}

// --- Download ---

async function download(file) {
	if (!requireSession()) {
		return;
	}

	try {
		const response = await request(`/api/files/${encodeURIComponent(file.id)}`, {
			failureMessage: MESSAGES.downloadFailed,
		});
		const blob = await response.blob();

		// A plain link to the API would carry no token, so the bytes are fetched here and handed to
		// the browser as a download of their own. The name comes from the listing, which already has
		// it, rather than from parsing Content-Disposition.
		const objectUrl = URL.createObjectURL(blob);
		const link = document.createElement('a');
		link.href = objectUrl;
		link.download = file.originalFilename;
		document.body.append(link);
		link.click();
		link.remove();
		URL.revokeObjectURL(objectUrl);
	}
	catch (error) {
		if (error instanceof SessionEnded) {
			return;
		}
		previewStatus.textContent = notFound(error) ? MESSAGES.previewMissing : MESSAGES.downloadFailed;
	}
}

// --- Upload ---

function uploadFailureMessage(error) {
	if (!(error instanceof ApiError)) {
		return MESSAGES.uploadFailed;
	}
	if (error.status === 413) {
		return MESSAGES.uploadTooLarge;
	}
	if (error.status === 400) {
		return MESSAGES.uploadRejected;
	}
	return error.message;
}

uploadForm.addEventListener('submit', async (event) => {
	event.preventDefault();

	if (uploading || !requireSession()) {
		return;
	}

	const file = fileInput.files[0];
	uploadError.textContent = '';
	if (!file) {
		uploadError.textContent = MESSAGES.uploadNoFile;
		return;
	}

	const body = new FormData();
	body.append('file', file);

	uploading = true;
	uploadButton.disabled = true;
	uploadButton.textContent = 'Загружаю…';
	try {
		await request('/api/files', { method: 'POST', body, failureMessage: MESSAGES.uploadFailed });
		uploadForm.reset();
		await loadList();
	}
	catch (error) {
		if (!(error instanceof SessionEnded)) {
			uploadError.textContent = uploadFailureMessage(error);
		}
	}
	finally {
		uploading = false;
		uploadButton.disabled = false;
		uploadButton.textContent = 'Загрузить';
	}
});

// --- Logout ---

logoutButton.addEventListener('click', () => {
	clearList();
	clearPreview();
	listStatus.textContent = '';
	goToLogin();
});

// A page restored from the browser's cache runs no script again, so the check is repeated here:
// after a logout, Back must not put the previous listing back on screen.
window.addEventListener('pageshow', () => {
	requireSession();
});

clearPreview();
loadList();
retryButton.addEventListener('click', loadList);
