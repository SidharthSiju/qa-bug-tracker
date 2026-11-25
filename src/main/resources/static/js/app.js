document.addEventListener('DOMContentLoaded', function () {
  initActivityTracker();
  initCrTestCaseFilter();
  initSidebarCollapse();
  initJiraClientFetch();

  // A confirmation on a single button, so one form can carry both a safe action and a destructive one.
  document.querySelectorAll('button[data-confirm]').forEach(function (button) {
    button.addEventListener('click', function (event) {
      if (!window.confirm(button.getAttribute('data-confirm'))) {
        event.preventDefault();
      }
    });
  });

  document.querySelectorAll('form[data-confirm]').forEach(function (form) {
    form.addEventListener('submit', function (event) {
      if (!window.confirm(form.getAttribute('data-confirm'))) {
        event.preventDefault();
      }
    });
  });

  document.querySelectorAll('form[data-disable-on-submit]').forEach(function (form) {
    form.addEventListener('submit', function () {
      var button = form.querySelector('[type="submit"]');
      if (button) {
        button.disabled = true;
        button.innerHTML = '<span class="spinner-border spinner-border-sm me-1"></span>Submitting...';
      }
    });
  });

  document.querySelectorAll('.auto-dismiss').forEach(function (el) {
    window.setTimeout(function () {
      var alert = bootstrap.Alert.getOrCreateInstance(el);
      if (alert) {
        alert.close();
      }
    }, 5000);
  });

  var sidebarToggle = document.getElementById('sidebarToggle');
  var sidebar = document.getElementById('appSidebar');
  if (sidebarToggle && sidebar) {
    sidebarToggle.addEventListener('click', function () {
      sidebar.classList.toggle('show');
    });
  }

  var logoutForm = document.getElementById('logoutForm');
  if (logoutForm) {
    logoutForm.addEventListener('submit', function (event) {
      if (!window.confirm('Are you sure you want to log out?')) {
        event.preventDefault();
      }
    });
  }

  document.querySelectorAll('select').forEach(enhanceSelect);

  document.querySelectorAll('[data-check-all]').forEach(function (master) {
    master.addEventListener('change', function () {
      document.querySelectorAll('input[name="ids"]').forEach(function (checkbox) {
        checkbox.checked = master.checked;
      });
    });
  });

  initSortableTables();
  propagateSortToPagination();
  syncTabUrl();
});

function syncTabUrl() {
  document.querySelectorAll('[data-bs-toggle="tab"]').forEach(function (tabEl) {
    tabEl.addEventListener('shown.bs.tab', function (event) {
      var target = event.target.getAttribute('href');
      if (!target || target.charAt(0) !== '#') {
        return;
      }
      var url = new URL(window.location.href);
      url.searchParams.set('tab', target.substring(1));
      window.history.replaceState({}, '', url.toString());
    });
  });
}

function propagateSortToPagination() {
  var params = new URLSearchParams(window.location.search);
  var sort = params.get('sort');
  var dir = params.get('dir');
  if (!sort) {
    return;
  }
  document.querySelectorAll('.pagination a.page-link').forEach(function (link) {
    var href = link.getAttribute('href');
    if (!href || href === '#') {
      return;
    }
    var url = new URL(href, window.location.origin);
    if (!url.searchParams.has('sort')) {
      url.searchParams.set('sort', sort);
    }
    if (!url.searchParams.has('dir') && dir) {
      url.searchParams.set('dir', dir);
    }
    link.setAttribute('href', url.pathname + url.search);
  });
}

function initSortableTables() {
  var params = new URLSearchParams(window.location.search);

  document.querySelectorAll('th[data-sort]').forEach(function (th) {
    var field = th.getAttribute('data-sort');
    var sortParam = th.getAttribute('data-sort-param') || 'sort';
    var dirParam = th.getAttribute('data-dir-param') || 'dir';
    var pageParam = th.getAttribute('data-page-param') || 'page';
    var activeSort = params.get(sortParam);
    var activeDir = (params.get(dirParam) || 'asc').toLowerCase();

    th.classList.add('sortable');

    var icon = document.createElement('i');
    icon.className = 'bi ms-1 sort-icon';
    if (activeSort === field) {
      icon.classList.add(activeDir === 'desc' ? 'bi-sort-down' : 'bi-sort-up');
      th.classList.add('sort-active');
    } else {
      icon.classList.add('bi-arrow-down-up');
    }
    th.appendChild(icon);

    th.addEventListener('click', function () {
      var url = new URL(window.location.href);
      var currentSort = url.searchParams.get(sortParam);
      var currentDir = (url.searchParams.get(dirParam) || 'asc').toLowerCase();
      var nextDir = (currentSort === field && currentDir === 'asc') ? 'desc' : 'asc';
      url.searchParams.set(sortParam, field);
      url.searchParams.set(dirParam, nextDir);
      url.searchParams.set(pageParam, '0');
      window.location.href = url.toString();
    });
  });
}

function enhanceSelect(select) {
  if (select.dataset.ssEnhanced || select.multiple || select.size > 1) {
    return;
  }
  var options = Array.prototype.slice.call(select.options);
  if (options.length === 0) {
    return;
  }
  select.dataset.ssEnhanced = 'true';

  var wrapper = document.createElement('div');
  wrapper.className = 'ss-wrap';
  select.parentNode.insertBefore(wrapper, select);
  wrapper.appendChild(select);
  select.classList.add('ss-native');

  var input = document.createElement('input');
  input.type = 'text';
  input.autocomplete = 'off';
  input.className = select.classList.contains('form-select-sm')
    ? 'form-control form-control-sm ss-input'
    : 'form-control ss-input';
  input.setAttribute('role', 'combobox');
  input.setAttribute('aria-expanded', 'false');
  var label = select.id ? document.querySelector('label[for="' + select.id + '"]') : null;
  if (label) {
    input.setAttribute('aria-label', label.textContent.trim());
  }
  var emptyOption = options[0] && options[0].value === '' ? options[0] : null;
  input.placeholder = emptyOption ? emptyOption.textContent.trim() : 'Type to search';
  if (select.required) {
    input.required = true;
  }
  if (select.disabled) {
    input.disabled = true;
  }

  var menu = document.createElement('div');
  menu.className = 'ss-menu';
  menu.hidden = true;

  wrapper.appendChild(input);
  wrapper.appendChild(menu);

  var filtered = [];
  var activeIndex = -1;

  function currentLabel() {
    var option = select.options[select.selectedIndex];
    return option && option.value !== '' ? option.textContent.trim() : '';
  }

  function syncFromSelect() {
    input.value = currentLabel();
  }

  // Lets external code refresh the searchable input after the native <select> is repopulated.
  select.ssSync = syncFromSelect;

  function renderMenu(filter) {
    var query = (filter || '').trim().toLowerCase();
    filtered = Array.prototype.slice.call(select.options).filter(function (option) {
      return !option.disabled && (!query || option.textContent.toLowerCase().indexOf(query) !== -1);
    });
    menu.innerHTML = '';
    activeIndex = -1;
    if (filtered.length === 0) {
      var empty = document.createElement('div');
      empty.className = 'ss-empty';
      empty.textContent = 'No matches';
      menu.appendChild(empty);
      return;
    }
    filtered.forEach(function (option) {
      var item = document.createElement('button');
      item.type = 'button';
      item.className = 'ss-option';
      item.textContent = option.textContent;
      item.addEventListener('mousedown', function (event) {
        event.preventDefault();
        choose(option);
      });
      menu.appendChild(item);
    });
  }

  function setActive(index) {
    var items = menu.querySelectorAll('.ss-option');
    if (items.length === 0) {
      return;
    }
    if (index < 0) {
      index = items.length - 1;
    }
    if (index >= items.length) {
      index = 0;
    }
    Array.prototype.forEach.call(items, function (el, i) {
      el.classList.toggle('active', i === index);
    });
    activeIndex = index;
    items[index].scrollIntoView({ block: 'nearest' });
  }

  function open() {
    renderMenu('');
    menu.hidden = false;
    input.setAttribute('aria-expanded', 'true');
  }

  function close() {
    menu.hidden = true;
    input.setAttribute('aria-expanded', 'false');
    activeIndex = -1;
  }

  function choose(option) {
    select.value = option.value;
    select.dispatchEvent(new Event('change', { bubbles: true }));
    syncFromSelect();
    if (select.required) {
      input.setCustomValidity('');
    }
    close();
    input.blur();
  }

  if (!select.disabled) {
    input.addEventListener('focus', function () {
      input.select();
      open();
    });
    input.addEventListener('input', function () {
      renderMenu(input.value);
      menu.hidden = false;
      input.setAttribute('aria-expanded', 'true');
    });
    input.addEventListener('keydown', function (event) {
      if (event.key === 'ArrowDown') {
        event.preventDefault();
        if (menu.hidden) {
          open();
        }
        setActive(activeIndex + 1);
      } else if (event.key === 'ArrowUp') {
        event.preventDefault();
        if (menu.hidden) {
          open();
        }
        setActive(activeIndex - 1);
      } else if (event.key === 'Enter') {
        if (!menu.hidden && activeIndex >= 0 && filtered[activeIndex]) {
          event.preventDefault();
          choose(filtered[activeIndex]);
        } else {
          close();
        }
      } else if (event.key === 'Escape') {
        close();
        syncFromSelect();
      } else if (event.key === 'Tab') {
        close();
      }
    });
    input.addEventListener('blur', function () {
      window.setTimeout(function () {
        close();
        if (input.value.trim() !== currentLabel()) {
          syncFromSelect();
        }
      }, 120);
    });
  }

  select.addEventListener('change', syncFromSelect);
  syncFromSelect();
}

// On the bug form, the Test Case dropdown lists only the selected change request's test cases.
function initCrTestCaseFilter() {
  var crSelect = document.getElementById('crId');
  var testCaseSelect = document.getElementById('testCaseId');
  if (!crSelect || !testCaseSelect) {
    return;
  }
  var url = testCaseSelect.getAttribute('data-test-cases-url');
  if (!url) {
    return;
  }
  var emptyLabel = testCaseSelect.getAttribute('data-empty-label') || 'No test case';
  crSelect.addEventListener('change', function () {
    var crId = crSelect.value;
    setSelectOptions(testCaseSelect, [], emptyLabel);
    if (!crId) {
      return;
    }
    fetch(url + '?crId=' + encodeURIComponent(crId), {
      headers: { Accept: 'application/json' },
      credentials: 'same-origin'
    })
      .then(function (response) {
        return response.ok ? response.json() : [];
      })
      .then(function (cases) {
        setSelectOptions(testCaseSelect, (cases || []).map(function (testCase) {
          return {
            value: testCase.id,
            label: testCase.testCaseNumber + ' - ' + (testCase.title || '')
          };
        }), emptyLabel);
      })
      .catch(function () {
        // leave just the empty option
      });
  });
}

function setSelectOptions(select, items, emptyLabel) {
  select.innerHTML = '';
  var empty = document.createElement('option');
  empty.value = '';
  empty.textContent = emptyLabel;
  select.appendChild(empty);
  (items || []).forEach(function (item) {
    var option = document.createElement('option');
    option.value = item.value;
    option.textContent = item.label;
    select.appendChild(option);
  });
  select.value = '';
  if (typeof select.ssSync === 'function') {
    select.ssSync();
  }
}

// The Administration menu in the sidebar collapses/expands, and remembers the choice between pages.
function initSidebarCollapse() {
  var adminNav = document.getElementById('adminNav');
  if (!adminNav) {
    return;
  }
  var key = 'cc.adminNavCollapsed';
  var toggle = document.querySelector('[data-bs-target="#adminNav"]');
  try {
    if (window.localStorage.getItem(key) === '1') {
      adminNav.classList.remove('show');
      if (toggle) {
        toggle.setAttribute('aria-expanded', 'false');
      }
    }
    adminNav.addEventListener('hidden.bs.collapse', function () {
      window.localStorage.setItem(key, '1');
    });
    adminNav.addEventListener('shown.bs.collapse', function () {
      window.localStorage.setItem(key, '0');
    });
  } catch (ignored) {
    // No storage (private mode): the menu simply starts expanded.
  }
}

// JIRA searched from the browser: for installations whose server has no network route to JIRA. The browser calls
// JIRA with the credentials typed on the page and posts the issues it received back to the application, which maps,
// reviews and imports them exactly like a server-side search.
function initJiraClientFetch() {
  var panel = document.getElementById('jiraClient');
  if (!panel) {
    return;
  }
  var endpoint = panel.dataset.jiraEndpoint || '';
  var jqlTemplate = panel.dataset.jiraJql || '';
  var fields = panel.dataset.jiraFields || '';
  var maxResults = parseInt(panel.dataset.jiraMax, 10) || 50;
  var postUrl = panel.dataset.jiraPost || '';
  var pageSize = 100;

  var emailInput = document.getElementById('jiraEmailInput');
  var tokenInput = document.getElementById('jiraTokenInput');
  var statusBox = document.getElementById('jiraClientStatus');
  var searchButton = document.getElementById('jiraBrowserSearchButton');
  var testButton = document.getElementById('jiraTestButton');
  var form = document.getElementById('jiraClientForm');
  var statusField = document.getElementById('status');
  var fromField = document.getElementById('fromDate');

  function report(kind, message) {
    statusBox.className = 'alert mb-0 alert-' + kind;
    statusBox.textContent = message;
  }

  function credentials() {
    var email = (emailInput.value || '').trim();
    var token = (tokenInput.value || '').trim();
    if (!email || !token) {
      report('warning', 'Enter your JIRA email and API token above first.');
      return null;
    }
    try {
      window.sessionStorage.setItem('cc.jiraEmail', email);
      window.sessionStorage.setItem('cc.jiraToken', token);
    } catch (ignored) {
      // private mode: the credentials simply will not be remembered
    }
    return {'email': email, 'token': token};
  }

  // Basic auth expects UTF-8; btoa() alone would fail on a non-Latin-1 address.
  function authorization(creds) {
    var raw = creds.email + ':' + creds.token;
    var binary = '';
    if (window.TextEncoder) {
      var bytes = new window.TextEncoder().encode(raw);
      for (var i = 0; i < bytes.length; i = i + 1) {
        binary = binary + String.fromCharCode(bytes[i]);
      }
    } else {
      binary = raw;
    }
    return 'Basic ' + window.btoa(binary);
  }

  // Mirrors JiraImportService.buildJql: the status is quoted when it contains a space.
  function buildJql() {
    var status = (statusField && statusField.value ? statusField.value : '').trim();
    if (status.indexOf(' ') >= 0 && status.charAt(0) !== '"') {
      status = '"' + status + '"';
    }
    var createdFrom = fromField && fromField.value ? fromField.value.trim() : '';
    return jqlTemplate.replace('{{status}}', status).replace('{{createdFrom}}', createdFrom).trim();
  }

  function searchUrl(jql, startAt, nextPageToken) {
    var url = endpoint;
    if (url.indexOf('?') < 0) {
      url = url + '?';
    } else {
      url = url + '&';
    }
    url = url + 'jql=' + encodeURIComponent(jql)
      + '&maxResults=' + pageSize
      + '&fields=' + encodeURIComponent(fields);
    if (nextPageToken) {
      url = url + '&nextPageToken=' + encodeURIComponent(nextPageToken);
    } else if (endpoint.indexOf('/search/jql') < 0) {
      url = url + '&startAt=' + startAt;
    }
    return url;
  }

  function myselfUrl() {
    var base = endpoint.replace(/\/rest\/.*$/, '');
    return base + (endpoint.indexOf('/api/3') >= 0 ? '/rest/api/3/myself' : '/rest/api/2/myself');
  }

  function callJira(url, auth) {
    return window.fetch(url, {
      'method': 'GET',
      'headers': {'Accept': 'application/json', 'Authorization': auth}
    }).then(function (response) {
      if (!response.ok) {
        return response.text().then(function (body) {
          var error = new Error('JIRA returned HTTP ' + response.status + (body ? ': ' + body.slice(0, 300) : ''));
          error.status = response.status;
          throw error;
        });
      }
      return response.json();
    });
  }

  // A blocked cross-origin call surfaces as an opaque TypeError, so name the usual cause instead of showing it raw.
  function describeFailure(error) {
    var message = String((error && error.message) || error);
    if (error && error.status === 401) {
      return 'JIRA rejected the credentials (401). Check your JIRA email and API token - Atlassian API tokens '
        + 'are created in your account settings.';
    }
    if (error && error.status === 403) {
      return 'JIRA refused the request (403): your account may not have permission to search these issues.';
    }
    if (message === 'Failed to fetch' || message.indexOf('NetworkError') >= 0 || message.indexOf('Load failed') >= 0) {
      return 'The browser could not reach JIRA at ' + endpoint + '. JIRA has to allow requests from this page: '
        + 'JIRA Cloud does not send the CORS headers a browser needs, so a JIRA Cloud URL can only be reached this '
        + 'way through a proxy or Atlassian\'s OAuth gateway. Otherwise check the URL, your browser\'s proxy '
        + 'settings, and that this machine can reach JIRA.';
    }
    return message;
  }

  function runSearch(creds) {
    var jql = buildJql();
    if (!statusField || !statusField.value || !fromField || !fromField.value) {
      report('warning', 'Choose a status and a "created from" date above first.');
      return;
    }
    if (!endpoint) {
      report('error', 'No JIRA URL is configured. An administrator must set it under Administration -> JIRA Configuration.');
      return;
    }
    report('secondary', 'Asking JIRA for up to ' + maxResults + ' issue(s) from this browser...');

    var auth = authorization(creds);
    var collected = [];

    function nextPage(nextPageToken, startAt) {
      return callJira(searchUrl(jql, startAt, nextPageToken), auth).then(function (data) {
        var issues = (data && data.issues) || [];
        for (var i = 0; i < issues.length; i = i + 1) {
          collected.push(issues[i]);
        }
        var tokenPaging = endpoint.indexOf('/search/jql') >= 0;
        var more = collected.length < maxResults;
        if (tokenPaging) {
          var token = data && data.nextPageToken;
          if (!more || !token || (data && data.isLast)) {
            return collected;
          }
          return nextPage(token, 0);
        }
        if (!more || issues.length < pageSize) {
          return collected;
        }
        return nextPage(null, startAt + issues.length);
      });
    }

    nextPage(null, 0).then(function (issues) {
      if (!issues.length) {
        report('warning', 'JIRA returned no issues for those filters.');
        return;
      }
      report('secondary', 'Got ' + issues.length + ' issue(s) from JIRA. Handing them to the application...');
      document.getElementById('jiraClientPayload').value = JSON.stringify({'issues': issues});
      document.getElementById('jiraClientStatusField').value = statusField.value;
      document.getElementById('jiraClientFromField').value = fromField.value;
      form.setAttribute('action', postUrl);
      form.submit();
    }, function (error) {
      report('error', describeFailure(error));
    });
  }

  function testConnection(creds) {
    report('secondary', 'Asking JIRA who you are...');
    callJira(myselfUrl(), authorization(creds)).then(function (data) {
      var who = (data && (data.displayName || data.name || data.emailAddress)) || 'your account';
      report('success', 'Connected to JIRA as ' + who + '. The browser can reach JIRA, so a search will work.');
    }, function (error) {
      report('error', describeFailure(error));
    });
  }

  if (searchButton) {
    searchButton.addEventListener('click', function () {
      var creds = credentials();
      if (creds) {
        runSearch(creds);
      }
    });
  }
  if (testButton) {
    testButton.addEventListener('click', function () {
      var creds = credentials();
      if (creds) {
        testConnection(creds);
      }
    });
  }

  // Remember the credentials for this tab only, so navigating back to the form does not mean typing them again.
  try {
    var savedEmail = window.sessionStorage.getItem('cc.jiraEmail');
    var savedToken = window.sessionStorage.getItem('cc.jiraToken');
    if (savedEmail && !emailInput.value) {
      emailInput.value = savedEmail;
    }
    if (savedToken) {
      tokenInput.value = savedToken;
    }
  } catch (ignored) {
    // no storage available
  }
}

// Records time spent per page (feature) for the ADMIN analytics screen.
function initActivityTracker() {
  var url = window.CC_ACTIVITY_URL;
  if (!url || !navigator.sendBeacon) {
    return;
  }
  var segmentStart = Date.now();
  function flush() {
    var seconds = Math.round((Date.now() - segmentStart) / 1000);
    var startedAt = segmentStart;
    segmentStart = Date.now();
    if (seconds < 1) {
      return;
    }
    try {
      navigator.sendBeacon(url, new Blob([JSON.stringify({
        path: window.location.pathname,
        seconds: seconds,
        startedAtEpochMs: startedAt
      })], { type: 'application/json' }));
    } catch (e) {
      // telemetry is best-effort
    }
  }
  window.addEventListener('pagehide', flush);
  document.addEventListener('visibilitychange', function () {
    if (document.visibilityState === 'hidden') {
      flush();
    }
  });
}

