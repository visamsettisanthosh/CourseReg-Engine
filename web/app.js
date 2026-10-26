/**
 * CourseReg Engine - Modern Course Registration & Timetable System
 * Academic Viva Edition - Interactive Frontend Application
 */

const API_BASE = "";

// Authentication & Session State
const authState = {
    token: localStorage.getItem("authToken") || null,
    user: JSON.parse(localStorage.getItem("authUser") || "null")
};

// Application State
const state = {
    students: [],
    courses: [],
    faculty: [],
    scenarios: [],
    currentStudentId: 1,
    studentDetails: null,
    validations: {}, // courseId -> validation result
    activeTab: "student-tab",
    riskFilter: "ALL",      // "ALL" | "HIGH" | "MEDIUM" | "LOW"
    adminRiskFilter: "ALL"  // "ALL" | "HIGH" | "MEDIUM" | "LOW"
};

// DOM Elements Cache
const elements = {
    // Auth & Navigation
    loginView: document.getElementById("login-view"),
    mainAppContainer: document.getElementById("main-app-container"),
    userSessionBar: document.getElementById("user-session-bar"),
    headerUserAvatar: document.getElementById("header-user-avatar"),
    headerUserName: document.getElementById("header-user-name"),
    headerUserRole: document.getElementById("header-user-role"),
    btnLogout: document.getElementById("btn-logout"),
    loginForm: document.getElementById("login-form"),
    loginUsername: document.getElementById("login-username"),
    loginPassword: document.getElementById("login-password"),
    loginSelectedRole: document.getElementById("login-selected-role"),
    loginErrorBanner: document.getElementById("login-error-banner"),
    btnLoginSubmit: document.getElementById("btn-login-submit"),
    loginBtnText: document.getElementById("login-btn-text"),
    loginBtnSpinner: document.getElementById("login-btn-spinner"),
    btnTogglePassword: document.getElementById("btn-toggle-password"),
    btnForgotPassword: document.getElementById("btn-forgot-password"),
    forgotPasswordModal: document.getElementById("forgot-password-modal"),
    accessDeniedView: document.getElementById("access-denied-view"),
    accessDeniedDetail: document.getElementById("access-denied-detail"),
    navTabs: document.querySelectorAll(".nav-tab"),
    tabContents: document.querySelectorAll(".tab-content"),
    navTabStudent: document.getElementById("nav-tab-student"),
    navTabRegistrar: document.getElementById("nav-tab-registrar"),
    navTabDag: document.getElementById("nav-tab-dag"),
    navTabAdmin: document.getElementById("nav-tab-admin"),
    scenariosButtonGrid: document.getElementById("scenarios-button-grid"),

    // Student Portal
    studentSelector: document.getElementById("student-selector"),
    studentMetaCard: document.getElementById("student-meta-card"),
    currentEnrollmentsList: document.getElementById("current-enrollments-list"),
    completedCoursesList: document.getElementById("completed-courses-list"),
    enrolledCountBadge: document.getElementById("enrolled-count-badge"),
    completedCountBadge: document.getElementById("completed-count-badge"),
    courseCatalogGrid: document.getElementById("course-catalog-grid"),
    courseSearch: document.getElementById("course-search"),
    
    // Registrar & Advisor
    kpiTotalStudents: document.getElementById("kpi-total-students"),
    kpiTotalCourses: document.getElementById("kpi-total-courses"),
    kpiHighRisk: document.getElementById("kpi-high-risk"),
    kpiMedRisk: document.getElementById("kpi-med-risk"),
    kpiLowRisk: document.getElementById("kpi-low-risk"),
    advisorAlertsTbody: document.getElementById("advisor-alerts-tbody"),
    advisorFilterStatus: document.getElementById("advisor-filter-status"),
    btnResetAdvisorFilter: document.getElementById("btn-reset-advisor-filter"),
    masterEnrollmentsTbody: document.getElementById("master-enrollments-tbody"),
    totalEnrollmentsBadge: document.getElementById("total-enrollments-badge"),
    btnRunRiskPipeline: document.getElementById("btn-run-risk-pipeline"),

    // Registrar Management Forms
    formAddCourse: document.getElementById("form-add-course"),
    formAddPrereq: document.getElementById("form-add-prereq"),
    formAddSlot: document.getElementById("form-add-slot"),
    formAddStudent: document.getElementById("form-add-student"),
    adminPrereqTarget: document.getElementById("admin-prereq-target"),
    adminPrereqSource: document.getElementById("admin-prereq-source"),
    adminSlotCourse: document.getElementById("admin-slot-course"),
    adminSlotFaculty: document.getElementById("admin-slot-faculty"),
    
    // DAG
    dagContainer: document.getElementById("dag-container"),
    dagCycleAlert: document.getElementById("dag-cycle-alert"),
    dagTopoOrder: document.getElementById("dag-topo-order"),
    
    // Toast & Modals
    toastContainer: document.getElementById("toast-container"),
    modalContainer: document.getElementById("modal-container"),
    modalTitle: document.getElementById("modal-title"),
    modalBodyContent: document.getElementById("modal-body-content"),
    modalFooterContent: document.getElementById("modal-footer-content"),
    modalCloseBtn: document.getElementById("modal-close-btn"),
    modalCancelBtn: document.getElementById("modal-cancel-btn"),
    modalConfirmBtn: document.getElementById("modal-confirm-btn"),

    // Edit Student Modal
    btnEditStudent: document.getElementById("btn-edit-student"),
    editModalContainer: document.getElementById("edit-modal-container"),
    editModalCloseBtn: document.getElementById("edit-modal-close-btn"),
    editModalCancelBtn: document.getElementById("edit-modal-cancel-btn"),
    editModalSaveBtn: document.getElementById("edit-modal-save-btn"),
    editName: document.getElementById("edit-name"),
    editEmail: document.getElementById("edit-email"),
    editGpa: document.getElementById("edit-gpa"),
    editAttendance: document.getElementById("edit-attendance"),
    editFailedCourses: document.getElementById("edit-failed-courses"),

    // Admin Dashboard
    adminKpiStudents: document.getElementById("admin-kpi-students"),
    adminKpiCourses: document.getElementById("admin-kpi-courses"),
    adminKpiRegistrations: document.getElementById("admin-kpi-registrations"),
    adminKpiHighRisk: document.getElementById("admin-kpi-high-risk"),
    adminKpiUsers: document.getElementById("admin-kpi-users"),
    adminUsersTbody: document.getElementById("admin-users-tbody"),
    adminStudentsTbody: document.getElementById("admin-students-tbody"),
    adminStudentFilterStatus: document.getElementById("admin-student-filter-status"),
    btnResetAdminFilter: document.getElementById("btn-reset-admin-filter"),
    adminCoursesTbody: document.getElementById("admin-courses-tbody"),
    adminPrereqsTbody: document.getElementById("admin-prereqs-tbody"),
    adminSlotsTbody: document.getElementById("admin-slots-tbody"),
    adminReportCoursesTbody: document.getElementById("admin-report-courses-tbody"),
    adminReportRisk: document.getElementById("admin-report-risk"),
    adminReportDept: document.getElementById("admin-report-dept"),
    adminAddUserBox: document.getElementById("admin-add-user-box"),
    adminResetPwModal: document.getElementById("admin-reset-pw-modal"),
    resetPwUserId: document.getElementById("reset-pw-user-id"),
    resetPwUsername: document.getElementById("reset-pw-username"),
    resetPwInput: document.getElementById("reset-pw-input")
};

// ----------------------------------------------------------------------------
// AUTHENTICATED FETCH HELPER
// ----------------------------------------------------------------------------
async function authFetch(url, options = {}) {
    options.headers = options.headers || {};
    if (authState.token) {
        options.headers["Authorization"] = `Bearer ${authState.token}`;
    }
    const res = await fetch(url, options);
    if (res.status === 401) {
        handleUnauthorized();
    }
    return res;
}

// ----------------------------------------------------------------------------
// INITIALIZATION
// ----------------------------------------------------------------------------
document.addEventListener("DOMContentLoaded", async () => {
    setupTabNavigation();
    setupAdminSubtabs();
    setupEventListeners();

    // Sign-up password visibility toggles
    const btnToggleSignupPw = document.getElementById("btn-toggle-signup-password");
    if (btnToggleSignupPw) {
        btnToggleSignupPw.addEventListener("click", () => {
            const pw = document.getElementById("signup-password");
            if (pw) {
                if (pw.type === "password") {
                    pw.type = "text";
                    btnToggleSignupPw.textContent = "🙈";
                } else {
                    pw.type = "password";
                    btnToggleSignupPw.textContent = "👁️";
                }
            }
        });
    }

    const btnToggleSignupConf = document.getElementById("btn-toggle-signup-confirm");
    if (btnToggleSignupConf) {
        btnToggleSignupConf.addEventListener("click", () => {
            const pw = document.getElementById("signup-confirm-password");
            if (pw) {
                if (pw.type === "password") {
                    pw.type = "text";
                    btnToggleSignupConf.textContent = "🙈";
                } else {
                    pw.type = "password";
                    btnToggleSignupConf.textContent = "👁️";
                }
            }
        });
    }

    const signupForm = document.getElementById("signup-form");
    if (signupForm) {
        signupForm.addEventListener("submit", handleSignupSubmit);
    }

    // Client-side route listeners for URL hashes and browser Back button
    window.addEventListener("hashchange", handleRouting);
    window.addEventListener("popstate", handleRouting);

    await checkAuthSession();
});

async function checkAuthSession() {
    if (authState.token) {
        try {
            const res = await authFetch(`${API_BASE}/api/auth/me`);
            if (res.ok) {
                const data = await res.json();
                if (data.authenticated && data.user) {
                    authState.user = data.user;
                    localStorage.setItem("authUser", JSON.stringify(data.user));
                    await applyAuthenticatedUI();
                    handleRouting();
                    return;
                }
            }
        } catch (e) {
            console.error("Auth check failed:", e);
        }
    }
    handleUnauthorized();
    handleRouting();
}

function handleRouting() {
    const hash = window.location.hash || "#/login";

    // 1. Unauthenticated Route Guard (Students are provisioned by Staff/Admin; no self-registration)
    if (!authState.token || !authState.user) {
        showLoginView(false);
        if (hash !== "#/login") {
            history.replaceState(null, "", "#/login");
        }
        return;
    }

    // 2. Authenticated Route Handling
    const role = (authState.user.role || "").toUpperCase();

    if (hash === "#/login" || hash === "#/register" || hash === "#/signup" || hash === "" || hash === "#") {
        if (role === "STUDENT") {
            window.location.hash = "#/dashboard";
        } else if (role === "ADMIN") {
            window.location.hash = "#/admin";
        } else {
            window.location.hash = "#/registrar";
        }
        return;
    }

    // Role-based Access Enforcement on URLs
    if (hash.startsWith("#/dashboard")) {
        switchTab("dashboard-tab", false);
    } else if (hash.startsWith("#/admin")) {
        if (role !== "ADMIN") {
            showAccessDenied("Only System Administrators can access the Admin Dashboard.");
            return;
        }
        switchTab("admin-tab", false);
    } else if (hash.startsWith("#/registrar")) {
        if (role === "STUDENT") {
            showAccessDenied("Students are not permitted to access Registrar and Advisor operations.");
            return;
        }
        switchTab("registrar-tab", false);
    } else if (hash.startsWith("#/dag")) {
        if (role === "STUDENT") {
            showAccessDenied("Students are not permitted to access Prerequisite DAG administration.");
            return;
        }
        switchTab("dag-tab", false);
    } else if (hash.startsWith("#/student") || hash.startsWith("#/courses")) {
        switchTab("student-tab", false);
    } else {
        returnToDashboard();
    }
}

async function applyAuthenticatedUI() {
    if (!authState.user) return;
    const user = authState.user;
    const role = (user.role || "").toUpperCase();

    // Show App, hide Login and Sign-up completely
    const loginView = document.getElementById("login-view");
    const signupView = document.getElementById("signup-view");
    const mainApp = document.getElementById("main-app-container");
    const userSessionBar = document.getElementById("user-session-bar");

    if (loginView) {
        loginView.classList.add("hidden");
        loginView.style.display = "none";
    }
    if (signupView) {
        signupView.classList.add("hidden");
        signupView.style.display = "none";
    }
    if (mainApp) {
        mainApp.classList.remove("hidden");
        mainApp.style.display = "flex";
    }
    if (userSessionBar) {
        userSessionBar.classList.remove("hidden");
        userSessionBar.style.display = "flex";
    }

    // Determine clean display name (defaulting to Santhosh S if not otherwise specified)
    const displayName = user.studentName || (user.username && user.username !== "student01" ? user.username : "Santhosh S");

    // Header Display
    if (elements.headerUserName) {
        elements.headerUserName.textContent = displayName;
    }
    if (elements.headerUserRole) {
        elements.headerUserRole.textContent = role === "STUDENT" ? "Student" : (role === "ADMIN" ? "Admin" : "Advisor");
    }

    const popoverName = document.getElementById("profile-popover-name");
    const popoverRole = document.getElementById("profile-popover-role");
    if (popoverName) popoverName.textContent = displayName;
    if (popoverRole) popoverRole.textContent = `${role} Account`;

    // Load initial data
    await loadInitialData();
    await loadScenarios();
    await loadFaculty();
    populateAdminDropdowns();

    // Role-specific View Configuration
    if (role === "STUDENT") {
        // Hide Viva Scenarios toolbar for students
        const vivaBanner = document.querySelector(".viva-scenarios-banner");
        if (vivaBanner) vivaBanner.style.display = "none";

        // Admin block in sidebar hidden for students
        const adminSideBlock = document.getElementById("sidebar-admin-block");
        if (adminSideBlock) adminSideBlock.classList.add("hidden");

        // Dashboard & Student Portal Welcome Headers
        const welcomeName = document.getElementById("student-portal-welcome-name");
        const welcomeRoll = document.getElementById("student-portal-welcome-roll");
        const dashWelcomeName = document.getElementById("dash-welcome-name");

        if (welcomeName) welcomeName.textContent = displayName;
        if (dashWelcomeName) dashWelcomeName.textContent = displayName;
        if (welcomeRoll) welcomeRoll.textContent = user.rollNumber || user.username || "266Q5A4508";

        // Hide student selector dropdown and edit button for student users
        const studentSelectBox = document.querySelector(".student-select-box");
        if (studentSelectBox) studentSelectBox.style.display = "none";
        if (elements.btnEditStudent) elements.btnEditStudent.style.display = "none";

        // Lock Student Selector to this student
        if (user.studentId) {
            state.currentStudentId = user.studentId;
            if (elements.studentSelector) {
                elements.studentSelector.value = user.studentId;
                elements.studentSelector.disabled = true;
            }
            await loadStudentData(user.studentId);
        } else {
            // Load default student (Aarav / Santhosh)
            await loadStudentData(state.currentStudentId || 1);
        }

        switchTab("dashboard-tab", false);
    } else if (role === "REGISTRAR" || role === "ADVISOR") {
        const vivaBanner = document.querySelector(".viva-scenarios-banner");
        if (vivaBanner) vivaBanner.style.display = "block";

        const adminSideBlock = document.getElementById("sidebar-admin-block");
        if (adminSideBlock) adminSideBlock.classList.remove("hidden");

        const studentSelectBox = document.querySelector(".student-select-box");
        if (studentSelectBox) studentSelectBox.style.display = "flex";
        if (elements.studentSelector) elements.studentSelector.disabled = false;
        if (elements.btnEditStudent) elements.btnEditStudent.style.display = "inline-flex";

        switchTab("registrar-tab", false);
    } else if (role === "ADMIN") {
        const vivaBanner = document.querySelector(".viva-scenarios-banner");
        if (vivaBanner) vivaBanner.style.display = "block";

        const adminSideBlock = document.getElementById("sidebar-admin-block");
        if (adminSideBlock) adminSideBlock.classList.remove("hidden");

        const studentSelectBox = document.querySelector(".student-select-box");
        if (studentSelectBox) studentSelectBox.style.display = "flex";
        if (elements.studentSelector) elements.studentSelector.disabled = false;
        if (elements.btnEditStudent) elements.btnEditStudent.style.display = "inline-flex";

        switchTab("admin-tab", false);
    }
}

function updateNavTabsForRole(role) {
    const nav = document.getElementById("main-nav-tabs");
    if (!nav) return;
    nav.classList.remove("hidden");
    nav.innerHTML = "";

    if (role === "STUDENT") {
        nav.innerHTML = `
            <button class="nav-tab active" data-tab="student-tab">Student Portal</button>
            <button class="nav-tab" data-scroll="catalog-panel">Courses</button>
            <button class="nav-tab" data-scroll="schedule-panel">Timetable</button>
            <button class="nav-tab" data-scroll="completed-panel">Prerequisites</button>
            <button class="nav-tab" data-scroll="meta-panel">Profile</button>
        `;
    } else if (role === "REGISTRAR" || role === "ADVISOR") {
        nav.innerHTML = `
            <button class="nav-tab active" data-tab="registrar-tab">Registrar & Advisor</button>
            <button class="nav-tab" data-tab="student-tab">Student Records</button>
            <button class="nav-tab" data-tab="dag-tab">Prerequisite DAG</button>
        `;
    } else if (role === "ADMIN") {
        nav.innerHTML = `
            <button class="nav-tab active" data-tab="admin-tab">Admin Dashboard</button>
            <button class="nav-tab" data-tab="registrar-tab">Registrar & Advisor</button>
            <button class="nav-tab" data-tab="student-tab">Student Records</button>
            <button class="nav-tab" data-tab="dag-tab">Prerequisite DAG</button>
        `;
    }

    nav.querySelectorAll(".nav-tab").forEach(tab => {
        tab.addEventListener("click", () => {
            if (tab.dataset.tab) {
                switchTab(tab.dataset.tab);
            } else if (tab.dataset.scroll) {
                scrollToStudentSection(tab.dataset.scroll);
            }
        });
    });
}

function scrollToStudentSection(section) {
    if (state.activeTab !== "student-tab") {
        switchTab("student-tab");
    }
    let target = null;
    if (section === "catalog-panel") {
        target = document.getElementById("course-catalog-grid");
    } else if (section === "schedule-panel") {
        target = document.getElementById("current-enrollments-list");
    } else if (section === "completed-panel") {
        target = document.getElementById("completed-courses-list");
    } else if (section === "meta-panel") {
        target = document.getElementById("student-meta-card");
    }
    if (target) {
        target.scrollIntoView({ behavior: "smooth", block: "start" });
    }
}

function handleUnauthorized() {
    authState.token = null;
    authState.user = null;
    localStorage.removeItem("authToken");
    localStorage.removeItem("authUser");
    sessionStorage.clear();

    const loginView = document.getElementById("login-view");
    const signupView = document.getElementById("signup-view");
    const mainApp = document.getElementById("main-app-container");
    const userSessionBar = document.getElementById("user-session-bar");

    if (loginView) {
        loginView.classList.remove("hidden");
        loginView.style.display = "flex";
    }
    if (signupView) {
        signupView.classList.add("hidden");
        signupView.style.display = "none";
    }
    if (mainApp) {
        mainApp.classList.add("hidden");
        mainApp.style.display = "none";
    }
    if (userSessionBar) {
        userSessionBar.classList.add("hidden");
        userSessionBar.style.display = "none";
    }
    if (elements.loginErrorBanner) elements.loginErrorBanner.classList.add("hidden");
    hideAccessDenied();

    const nav = document.getElementById("main-nav-tabs");
    if (nav) {
        nav.classList.add("hidden");
        nav.style.display = "none";
        nav.innerHTML = "";
    }
}

function setupTabNavigation() {
    elements.navTabs.forEach(tab => {
        tab.addEventListener("click", () => {
            const targetId = tab.dataset.tab;
            if (targetId) switchTab(targetId);
        });
    });
}

function switchTab(targetId, updateHash = true) {
    if (!authState.user) {
        handleUnauthorized();
        return;
    }

    const role = (authState.user.role || "").toUpperCase();

    // Strict Role-based Access Guards
    if (targetId === "admin-tab" && role !== "ADMIN") {
        showAccessDenied("Only System Administrators can access the Admin Dashboard.");
        return;
    }

    if ((targetId === "registrar-tab" || targetId === "dag-tab") && role === "STUDENT") {
        showAccessDenied("Students are not permitted to access administrative operations.");
        return;
    }

    hideAccessDenied();

    const nav = document.getElementById("main-nav-tabs");
    if (nav) {
        nav.querySelectorAll(".nav-tab").forEach(t => {
            if (t.dataset.tab === targetId) {
                t.classList.add("active");
            } else if (t.dataset.tab) {
                t.classList.remove("active");
            }
        });
    }

    // Update active state in app-sidebar
    document.querySelectorAll(".sidebar-item").forEach(item => {
        const v = item.dataset.view;
        if ((targetId === "dashboard-tab" && (v === "dashboard-tab" || v === "timetable-view")) ||
            (targetId === "student-tab" && (v === "registration-view" || v === "my-courses-view" || v === "academic-records-view" || v === "profile-view")) ||
            (targetId === "registrar-tab" && v === "registrar-view") ||
            (targetId === "dag-tab" && v === "dag-view") ||
            (targetId === "admin-tab" && v === "admin-view")) {
            item.classList.add("active");
        } else if (v) {
            item.classList.remove("active");
        }
    });

    document.querySelectorAll(".tab-content").forEach(c => c.classList.remove("active"));
    const tabSection = document.getElementById(targetId);
    if (tabSection) tabSection.classList.add("active");

    state.activeTab = targetId;

    if (targetId === "dashboard-tab") {
        updateDashboardStats();
        if (updateHash) history.pushState(null, "", "#/dashboard");
    } else if (targetId === "registrar-tab") {
        loadRegistrarDashboard();
        if (updateHash) history.pushState(null, "", "#/registrar");
    } else if (targetId === "dag-tab") {
        renderDagVisualizer();
        if (updateHash) history.pushState(null, "", "#/dag");
    } else if (targetId === "admin-tab") {
        loadAdminDashboard();
        if (updateHash) history.pushState(null, "", "#/admin");
    } else if (targetId === "student-tab") {
        if (updateHash) history.pushState(null, "", "#/student");
    }
}

function showAccessDenied(detailMessage) {
    if (elements.mainAppContainer) elements.mainAppContainer.classList.remove("hidden");
    document.querySelectorAll(".tab-content").forEach(c => c.classList.remove("active"));
    const nav = document.getElementById("main-nav-tabs");
    if (nav) nav.querySelectorAll(".nav-tab").forEach(t => t.classList.remove("active"));

    if (elements.accessDeniedDetail) {
        elements.accessDeniedDetail.textContent = detailMessage || "You don't have permission to access this page.";
    }
    if (elements.accessDeniedView) {
        elements.accessDeniedView.classList.remove("hidden");
    }
}

function hideAccessDenied() {
    if (elements.accessDeniedView) {
        elements.accessDeniedView.classList.add("hidden");
    }
}

window.returnToDashboard = function() {
    hideAccessDenied();
    if (!authState.user) {
        handleUnauthorized();
        return;
    }
    const role = (authState.user.role || "").toUpperCase();
    if (role === "STUDENT") {
        switchTab("student-tab");
    } else if (role === "ADMIN") {
        switchTab("admin-tab");
    } else {
        switchTab("registrar-tab");
    }
};

window.showSignupView = function(updateHash = true) {
    const loginView = document.getElementById("login-view");
    const signupView = document.getElementById("signup-view");
    const mainApp = document.getElementById("main-app-container");

    if (loginView) {
        loginView.classList.add("hidden");
        loginView.style.display = "none";
    }
    if (mainApp) {
        mainApp.classList.add("hidden");
        mainApp.style.display = "none";
    }
    if (signupView) {
        signupView.classList.remove("hidden");
        signupView.style.display = "flex";
    }
    const successBox = document.getElementById("signup-success-box");
    if (successBox) {
        successBox.classList.add("hidden");
        successBox.style.display = "none";
    }
    const signupForm = document.getElementById("signup-form");
    if (signupForm) {
        signupForm.classList.remove("hidden");
        signupForm.style.display = "block";
        signupForm.reset();
    }
    const errBanner = document.getElementById("signup-error-banner");
    if (errBanner) {
        errBanner.classList.add("hidden");
        errBanner.style.display = "none";
    }
    if (updateHash) history.pushState(null, "", "#/register");
};

window.showLoginView = function(updateHash = true) {
    const signupView = document.getElementById("signup-view");
    const loginView = document.getElementById("login-view");
    const mainApp = document.getElementById("main-app-container");

    if (signupView) {
        signupView.classList.add("hidden");
        signupView.style.display = "none";
    }
    if (mainApp) {
        mainApp.classList.add("hidden");
        mainApp.style.display = "none";
    }
    if (loginView) {
        loginView.classList.remove("hidden");
        loginView.style.display = "flex";
    }
    hideAccessDenied();
    if (updateHash) history.pushState(null, "", "#/login");
};

window.showLoginViewWithRoll = function() {
    const signupRollInput = document.getElementById("signup-roll");
    const roll = signupRollInput ? signupRollInput.value.trim().toUpperCase() : "";
    showLoginView();
    const studentPill = document.getElementById("role-btn-student");
    if (studentPill) studentPill.click();
    if (elements.loginUsername) {
        elements.loginUsername.value = roll;
    }
    if (elements.loginPassword) {
        elements.loginPassword.focus();
    }
};

function updateLoginFormForRole(role) {
    const usernameLabel = document.getElementById("login-username-label");
    const usernameInput = document.getElementById("login-username");
    const signupPrompt = document.getElementById("student-signup-prompt");
    if (usernameLabel) usernameLabel.textContent = "Email or Username";
    if (usernameInput) {
        usernameInput.placeholder = "Enter your email or username";
        usernameInput.setAttribute("autocomplete", "username");
    }
    if (signupPrompt) signupPrompt.classList.add("hidden");
}

function setupEventListeners() {
    // Auth listeners
    if (elements.loginForm) {
        elements.loginForm.addEventListener("submit", handleLoginSubmit);
    }
    if (elements.btnLogout) {
        elements.btnLogout.addEventListener("click", handleLogout);
    }
    if (elements.btnTogglePassword) {
        elements.btnTogglePassword.addEventListener("click", () => {
            if (elements.loginPassword.type === "password") {
                elements.loginPassword.type = "text";
                elements.btnTogglePassword.textContent = "🙈";
            } else {
                elements.loginPassword.type = "password";
                elements.btnTogglePassword.textContent = "👁️";
            }
        });
    }
    if (elements.btnForgotPassword) {
        elements.btnForgotPassword.addEventListener("click", () => {
            openForgotPasswordModal();
        });
    }

    // Forgot Password OTP form listeners
    const fpStep1 = document.getElementById("fp-form-step1");
    if (fpStep1) fpStep1.addEventListener("submit", handleFpStep1Submit);

    const fpStep2 = document.getElementById("fp-form-step2");
    if (fpStep2) fpStep2.addEventListener("submit", handleFpStep2Submit);

    const fpStep3 = document.getElementById("fp-form-step3");
    if (fpStep3) fpStep3.addEventListener("submit", handleFpStep3Submit);

    const fpStep4 = document.getElementById("fp-form-step4");
    if (fpStep4) fpStep4.addEventListener("submit", handleFpStep4Submit);

    const fpResendBtn = document.getElementById("fp-btn-resend-otp");
    if (fpResendBtn) fpResendBtn.addEventListener("click", handleFpResendOtp);

    const toggleFpNewPw = document.getElementById("btn-toggle-fp-new-pw");
    if (toggleFpNewPw) {
        toggleFpNewPw.addEventListener("click", () => {
            const pw = document.getElementById("fp-new-password");
            if (pw) {
                if (pw.type === "password") {
                    pw.type = "text";
                    toggleFpNewPw.textContent = "🙈";
                } else {
                    pw.type = "password";
                    toggleFpNewPw.textContent = "👁️";
                }
            }
        });
    }

    const toggleFpConfirmPw = document.getElementById("btn-toggle-fp-confirm-pw");
    if (toggleFpConfirmPw) {
        toggleFpConfirmPw.addEventListener("click", () => {
            const pw = document.getElementById("fp-confirm-password");
            if (pw) {
                if (pw.type === "password") {
                    pw.type = "text";
                    toggleFpConfirmPw.textContent = "🙈";
                } else {
                    pw.type = "password";
                    toggleFpConfirmPw.textContent = "👁️";
                }
            }
        });
    }

    // Registrar Student Details Lookup listeners
    const formStudentLookup = document.getElementById("form-student-lookup");
    if (formStudentLookup) formStudentLookup.addEventListener("submit", handleStudentLookupSearch);

    const clearStudentLookupBtn = document.getElementById("btn-registrar-clear-student");
    if (clearStudentLookupBtn) clearStudentLookupBtn.addEventListener("click", clearStudentLookup);

    const btnGotoSignup = document.getElementById("btn-goto-signup");
    if (btnGotoSignup) {
        btnGotoSignup.addEventListener("click", (e) => {
            e.preventDefault();
            showSignupView();
        });
    }

    // Role switcher pills on Login Form
    document.querySelectorAll(".role-pill").forEach(pill => {
        pill.addEventListener("click", () => {
            document.querySelectorAll(".role-pill").forEach(p => p.classList.remove("active"));
            pill.classList.add("active");
            const role = pill.dataset.role;
            if (elements.loginSelectedRole) {
                elements.loginSelectedRole.value = role;
            }
            updateLoginFormForRole(role);
        });
    });

    elements.studentSelector.addEventListener("change", (e) => {
        state.currentStudentId = parseInt(e.target.value);
        loadStudentData(state.currentStudentId);
    });

    elements.courseSearch.addEventListener("input", (e) => {
        filterCourseCatalog(e.target.value.toLowerCase());
    });

    if (elements.btnRunRiskPipeline) {
        elements.btnRunRiskPipeline.addEventListener("click", runRiskPipeline);
    }

    elements.modalCloseBtn.addEventListener("click", hideModal);
    elements.modalCancelBtn.addEventListener("click", hideModal);

    // Edit modal listeners
    if (elements.btnEditStudent) {
        elements.btnEditStudent.addEventListener("click", showEditModal);
    }
    if (elements.editModalCloseBtn) {
        elements.editModalCloseBtn.addEventListener("click", hideEditModal);
    }
    if (elements.editModalCancelBtn) {
        elements.editModalCancelBtn.addEventListener("click", hideEditModal);
    }
    if (elements.editModalSaveBtn) {
        elements.editModalSaveBtn.addEventListener("click", saveStudentProfile);
    }

    // Registrar forms listeners
    if (elements.formAddCourse) {
        elements.formAddCourse.addEventListener("submit", handleAddCourse);
    }
    if (elements.formAddPrereq) {
        elements.formAddPrereq.addEventListener("submit", handleAddPrereq);
    }
    if (elements.formAddSlot) {
        elements.formAddSlot.addEventListener("submit", handleAddSlot);
    }
    if (elements.formAddStudent) {
        elements.formAddStudent.addEventListener("submit", handleAddStudent);
    }

    // ------------------------------------------------------------------------
    // RISK SCENARIO FILTERS (Registrar & Advisor Dashboard)
    // ------------------------------------------------------------------------
    // Registrar KPI Cards click (All, High, Medium, Low)
    document.querySelectorAll("#registrar-tab .kpi-filter-card").forEach(card => {
        card.addEventListener("click", () => {
            const risk = card.dataset.risk || "ALL";
            filterRegistrarByRisk(risk);
        });
    });

    // Advisor Risk Filter Buttons click
    document.querySelectorAll("#advisor-risk-filter-group .btn-filter-risk").forEach(btn => {
        btn.addEventListener("click", () => {
            const risk = btn.dataset.risk || "ALL";
            filterRegistrarByRisk(risk);
        });
    });

    // Clear / Reset Advisor Filter Button
    if (elements.btnResetAdvisorFilter) {
        elements.btnResetAdvisorFilter.addEventListener("click", () => {
            filterRegistrarByRisk("ALL");
        });
    }

    // ------------------------------------------------------------------------
    // RISK SCENARIO FILTERS (Admin Dashboard)
    // ------------------------------------------------------------------------
    // Admin Overview High Risk Card click -> filters and navigates to Student Records
    const adminKpiHigh = document.getElementById("admin-kpi-filter-high");
    if (adminKpiHigh) {
        adminKpiHigh.addEventListener("click", () => {
            filterAdminStudentsByRisk("HIGH");
        });
    }

    // Admin Overview Total Students Card click -> navigates to all students
    const adminKpiAll = document.getElementById("admin-kpi-filter-students");
    if (adminKpiAll) {
        adminKpiAll.addEventListener("click", () => {
            filterAdminStudentsByRisk("ALL");
        });
    }

    // Admin Student Records Subtab Risk Filter Buttons click
    document.querySelectorAll("#admin-student-risk-filter-group .btn-filter-risk").forEach(btn => {
        btn.addEventListener("click", () => {
            const risk = btn.dataset.risk || "ALL";
            state.adminRiskFilter = risk;
            loadAdminStudents();
        });
    });

    // Clear / Reset Admin Filter Button
    if (elements.btnResetAdminFilter) {
        elements.btnResetAdminFilter.addEventListener("click", () => {
            state.adminRiskFilter = "ALL";
            loadAdminStudents();
        });
    }

    setupDashboardAndSidebarNavigation();
}

function setupDashboardAndSidebarNavigation() {
    // 1. Sidebar items navigation
    const sideDashboard = document.getElementById("side-item-dashboard");
    if (sideDashboard) {
        sideDashboard.addEventListener("click", () => switchTab("dashboard-tab"));
    }

    const sideTimetable = document.getElementById("side-item-timetable");
    if (sideTimetable) {
        sideTimetable.addEventListener("click", () => {
            switchTab("dashboard-tab");
            document.getElementById("timetable-card")?.scrollIntoView({ behavior: "smooth" });
        });
    }

    const sideRegistration = document.getElementById("side-item-registration");
    if (sideRegistration) {
        sideRegistration.addEventListener("click", () => {
            switchTab("student-tab");
            document.getElementById("course-catalog-grid")?.scrollIntoView({ behavior: "smooth" });
        });
    }

    const sideCourses = document.getElementById("side-item-courses");
    if (sideCourses) {
        sideCourses.addEventListener("click", () => {
            switchTab("student-tab");
            document.getElementById("current-enrollments-list")?.scrollIntoView({ behavior: "smooth" });
        });
    }

    const sideRecords = document.getElementById("side-item-records");
    if (sideRecords) {
        sideRecords.addEventListener("click", () => {
            switchTab("student-tab");
            document.getElementById("completed-courses-list")?.scrollIntoView({ behavior: "smooth" });
        });
    }

    const sideProfile = document.getElementById("side-item-profile");
    if (sideProfile) {
        sideProfile.addEventListener("click", () => {
            switchTab("student-tab");
            document.getElementById("student-meta-card")?.scrollIntoView({ behavior: "smooth" });
        });
    }

    const sideSettings = document.getElementById("side-item-settings");
    if (sideSettings) {
        sideSettings.addEventListener("click", () => {
            showToast("System settings & preferences are up to date.", "info");
        });
    }

    const sideLogout = document.getElementById("side-item-logout");
    if (sideLogout) {
        sideLogout.addEventListener("click", handleLogout);
    }

    const sideRegistrar = document.getElementById("side-item-registrar");
    if (sideRegistrar) {
        sideRegistrar.addEventListener("click", () => switchTab("registrar-tab"));
    }

    const sideDag = document.getElementById("side-item-dag");
    if (sideDag) {
        sideDag.addEventListener("click", () => switchTab("dag-tab"));
    }

    const sideAdmin = document.getElementById("side-item-admin");
    if (sideAdmin) {
        sideAdmin.addEventListener("click", () => switchTab("admin-tab"));
    }

    // 2. Quick Action Buttons
    const btnQaRegister = document.getElementById("btn-qa-register");
    if (btnQaRegister) {
        btnQaRegister.addEventListener("click", () => {
            switchTab("student-tab");
            document.getElementById("course-catalog-grid")?.scrollIntoView({ behavior: "smooth" });
        });
    }

    const btnQaTimetable = document.getElementById("btn-qa-timetable");
    if (btnQaTimetable) {
        btnQaTimetable.addEventListener("click", () => {
            document.getElementById("timetable-card")?.scrollIntoView({ behavior: "smooth" });
        });
    }

    const btnQaCourses = document.getElementById("btn-qa-courses");
    if (btnQaCourses) {
        btnQaCourses.addEventListener("click", () => {
            switchTab("student-tab");
            document.getElementById("current-enrollments-list")?.scrollIntoView({ behavior: "smooth" });
        });
    }

    const btnQaRecords = document.getElementById("btn-qa-records");
    if (btnQaRecords) {
        btnQaRecords.addEventListener("click", () => {
            switchTab("student-tab");
            document.getElementById("completed-courses-list")?.scrollIntoView({ behavior: "smooth" });
        });
    }

    // 3. KPI Links
    const linkCourses = document.getElementById("dash-link-courses");
    if (linkCourses) {
        linkCourses.addEventListener("click", () => {
            switchTab("student-tab");
            document.getElementById("current-enrollments-list")?.scrollIntoView({ behavior: "smooth" });
        });
    }

    const linkTimetable = document.getElementById("dash-link-timetable");
    if (linkTimetable) {
        linkTimetable.addEventListener("click", () => {
            document.getElementById("timetable-card")?.scrollIntoView({ behavior: "smooth" });
        });
    }

    const linkRegister = document.getElementById("dash-link-register");
    if (linkRegister) {
        linkRegister.addEventListener("click", () => {
            switchTab("student-tab");
            document.getElementById("course-catalog-grid")?.scrollIntoView({ behavior: "smooth" });
        });
    }

    const linkRecords = document.getElementById("dash-link-records");
    if (linkRecords) {
        linkRecords.addEventListener("click", () => {
            switchTab("student-tab");
            document.getElementById("completed-courses-list")?.scrollIntoView({ behavior: "smooth" });
        });
    }

    // 4. Notification & Profile Popovers
    const notifBtn = document.getElementById("topbar-notification-btn");
    const notifPopover = document.getElementById("notification-popover");
    if (notifBtn && notifPopover) {
        notifBtn.addEventListener("click", (e) => {
            e.stopPropagation();
            notifPopover.classList.toggle("hidden");
            document.getElementById("profile-popover")?.classList.add("hidden");
        });
    }

    const profileTrigger = document.getElementById("topbar-profile-trigger");
    const profilePopover = document.getElementById("profile-popover");
    if (profileTrigger && profilePopover) {
        profileTrigger.addEventListener("click", (e) => {
            e.stopPropagation();
            profilePopover.classList.toggle("hidden");
            document.getElementById("notification-popover")?.classList.add("hidden");
        });
    }

    const profileSignout = document.getElementById("profile-signout-link");
    if (profileSignout) {
        profileSignout.addEventListener("click", handleLogout);
    }

    const profileMenuLink = document.getElementById("profile-menu-link");
    if (profileMenuLink) {
        profileMenuLink.addEventListener("click", () => {
            profilePopover?.classList.add("hidden");
            switchTab("student-tab");
            document.getElementById("student-meta-card")?.scrollIntoView({ behavior: "smooth" });
        });
    }

    const profileSettingsLink = document.getElementById("profile-settings-link");
    if (profileSettingsLink) {
        profileSettingsLink.addEventListener("click", () => {
            profilePopover?.classList.add("hidden");
            showToast("Settings: Preferences and notification options are up to date.", "info");
        });
    }

    document.addEventListener("click", () => {
        notifPopover?.classList.add("hidden");
        profilePopover?.classList.add("hidden");
    });

    // 5. Hamburger Sidebar Toggle
    const btnToggleSidebar = document.getElementById("btn-toggle-sidebar");
    const sidebar = document.getElementById("app-sidebar");
    if (btnToggleSidebar && sidebar) {
        btnToggleSidebar.addEventListener("click", () => {
            sidebar.classList.toggle("open");
        });
    }

    // 6. Topbar Search Input
    const topSearch = document.getElementById("topbar-search-input");
    if (topSearch) {
        topSearch.addEventListener("input", (e) => {
            const query = e.target.value.trim().toLowerCase();
            if (query.length > 0 && state.activeTab !== "student-tab") {
                switchTab("student-tab");
            }
            if (elements.courseSearch) {
                elements.courseSearch.value = e.target.value;
            }
            filterCourseCatalog(query);
        });
    }

    // 7. Live Clock
    updateLiveClock();
    setInterval(updateLiveClock, 1000);
}

function updateLiveClock() {
    const dateEl = document.getElementById("dash-date-display");
    const timeEl = document.getElementById("dash-time-display");
    const pickerLabel = document.getElementById("timetable-picker-label");
    
    const now = new Date();
    const days = ["Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"];
    const months = ["Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"];
    
    const dayName = days[now.getDay()];
    const dateNum = now.getDate();
    const monthName = months[now.getMonth()];
    const year = now.getFullYear();
    
    let hours = now.getHours();
    const ampm = hours >= 12 ? "PM" : "AM";
    hours = hours % 12;
    hours = hours ? hours : 12;
    const minutes = String(now.getMinutes()).padStart(2, "0");
    
    const dateStr = `${dayName}, ${dateNum} ${monthName} ${year}`;
    const timeStr = `${String(hours).padStart(2, "0")}:${minutes} ${ampm}`;

    if (dateEl) dateEl.textContent = dateStr;
    if (timeEl) timeEl.textContent = timeStr;
    if (pickerLabel) pickerLabel.textContent = dateStr;
}

function updateDashboardStats() {
    const s = state.studentDetails;
    const coursesCount = (s && s.enrollments) ? s.enrollments.filter(e => e.status === "ENROLLED").length : 6;
    const statCourses = document.getElementById("dash-stat-courses");
    if (statCourses) statCourses.textContent = coursesCount || 6;

    const statClasses = document.getElementById("dash-stat-classes");
    if (statClasses) statClasses.textContent = 5;

    const statPending = document.getElementById("dash-stat-pending");
    if (statPending) statPending.textContent = 2;

    const statProgress = document.getElementById("dash-stat-progress");
    if (statProgress) {
        if (s && s.gpa !== undefined) {
            const pct = Math.min(100, Math.round((s.gpa / 4.0) * 100));
            statProgress.textContent = `${pct}%`;
        } else {
            statProgress.textContent = "78%";
        }
    }

    if (s) {
        const metricValAtt = document.getElementById("metric-val-att");
        const metricBarAtt = document.getElementById("metric-bar-att");
        if (metricValAtt && s.attendancePct !== undefined) {
            metricValAtt.textContent = `${Math.round(s.attendancePct)}%`;
            if (metricBarAtt) metricBarAtt.style.width = `${Math.round(s.attendancePct)}%`;
        }

        const metricValGpa = document.getElementById("metric-val-gpa");
        const metricBarGpa = document.getElementById("metric-bar-gpa");
        if (metricValGpa && s.gpa !== undefined) {
            const pct = Math.min(100, Math.round((s.gpa / 4.0) * 100));
            metricValGpa.textContent = `${pct}%`;
            if (metricBarGpa) metricBarGpa.style.width = `${pct}%`;
        }
    }
}

// Global filter helper for Registrar / Advisor dashboard
window.filterRegistrarByRisk = function(risk) {
    state.riskFilter = risk;
    loadRegistrarDashboard();
    // Scroll down to the table smoothly so user sees the filtered students
    const tablePanel = document.querySelector("#advisor-alerts-tbody");
    if (tablePanel) {
        tablePanel.closest(".panel")?.scrollIntoView({ behavior: "smooth", block: "start" });
    }
};

// Global filter helper for Admin dashboard
window.filterAdminStudentsByRisk = function(risk) {
    state.adminRiskFilter = risk;
    // Switch to Student Records subtab inside admin dashboard
    const subtabBtn = document.querySelector('.admin-subtab[data-subtab="admin-subtab-students"]');
    if (subtabBtn) {
        subtabBtn.click();
    } else {
        loadAdminStudents();
    }
    const adminTable = document.querySelector("#admin-students-tbody");
    if (adminTable) {
        adminTable.closest(".table-container")?.scrollIntoView({ behavior: "smooth", block: "start" });
    }
};

async function handleSignupSubmit(e) {
    e.preventDefault();
    const nameInput = document.getElementById("signup-name");
    const rollInput = document.getElementById("signup-roll");
    const pwInput = document.getElementById("signup-password");
    const confirmInput = document.getElementById("signup-confirm-password");
    const errorBanner = document.getElementById("signup-error-banner");
    const submitBtn = document.getElementById("btn-signup-submit");
    const btnText = document.getElementById("signup-btn-text");
    const btnSpinner = document.getElementById("signup-btn-spinner");

    const name = nameInput ? nameInput.value.trim() : "";
    const rollNumber = rollInput ? rollInput.value.trim().toUpperCase() : "";
    const password = pwInput ? pwInput.value : "";
    const confirmPassword = confirmInput ? confirmInput.value : "";

    // Frontend Validation
    if (!name) {
        showSignupError("Please enter your full name.");
        return;
    }
    if (!rollNumber) {
        showSignupError("Please enter your roll number.");
        return;
    }
    if (!password) {
        showSignupError("Please enter a password.");
        return;
    }
    if (password.length < 6) {
        showSignupError("Password must be at least 6 characters long.");
        return;
    }
    if (password !== confirmPassword) {
        showSignupError("Passwords do not match.");
        return;
    }

    if (errorBanner) errorBanner.classList.add("hidden");
    if (submitBtn) submitBtn.disabled = true;
    if (btnText) btnText.textContent = "Creating Account...";
    if (btnSpinner) btnSpinner.classList.remove("hidden");

    try {
        const res = await fetch(`${API_BASE}/api/auth/register-student`, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ name, rollNumber, password, confirmPassword })
        });
        const data = await res.json();
        if (res.ok && data.success) {
            const signupForm = document.getElementById("signup-form");
            const successBox = document.getElementById("signup-success-box");
            if (signupForm) signupForm.classList.add("hidden");
            if (successBox) successBox.classList.remove("hidden");
            showToast("Account created successfully! Please sign in.", "success");
        } else {
            showSignupError(data.error || "Could not create student account. Please verify your details.");
        }
    } catch (err) {
        showSignupError("Connection error during account creation. Please try again.");
    } finally {
        if (submitBtn) submitBtn.disabled = false;
        if (btnText) btnText.textContent = "Create Account";
        if (btnSpinner) btnSpinner.classList.add("hidden");
    }
}

function showSignupError(msg) {
    const errorBanner = document.getElementById("signup-error-banner");
    if (errorBanner) {
        errorBanner.innerHTML = `<span>⚠️</span> <span>${escapeHtml(msg)}</span>`;
        errorBanner.classList.remove("hidden");
    }
}

async function handleLoginSubmit(e) {
    e.preventDefault();
    const username = elements.loginUsername.value.trim();
    const password = elements.loginPassword.value.trim();
    const requestedRole = elements.loginSelectedRole ? elements.loginSelectedRole.value : "";

    if (!username || !password) {
        const missingMsg = requestedRole === "STUDENT"
            ? "Roll number and password are required."
            : "Username and password are required.";
        showLoginError(missingMsg);
        return;
    }

    if (elements.loginErrorBanner) elements.loginErrorBanner.classList.add("hidden");
    if (elements.btnLoginSubmit) elements.btnLoginSubmit.disabled = true;
    if (elements.loginBtnText) elements.loginBtnText.textContent = "Signing In...";
    if (elements.loginBtnSpinner) elements.loginBtnSpinner.classList.remove("hidden");

    try {
        const res = await fetch(`${API_BASE}/api/auth/login`, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ username, password, requestedRole })
        });
        const data = await res.json();
        if (data.success && data.token) {
            authState.token = data.token;
            authState.user = data.user;
            localStorage.setItem("authToken", data.token);
            localStorage.setItem("authUser", JSON.stringify(data.user));
            showToast(`Welcome back, ${data.user.studentName || data.user.username}!`, "success");

            // Explicitly ensure login and signup disappear immediately
            const loginView = document.getElementById("login-view");
            if (loginView) {
                loginView.classList.add("hidden");
                loginView.style.display = "none";
            }
            const signupView = document.getElementById("signup-view");
            if (signupView) {
                signupView.classList.add("hidden");
                signupView.style.display = "none";
            }
            const mainApp = document.getElementById("main-app-container");
            if (mainApp) {
                mainApp.classList.remove("hidden");
                mainApp.style.display = "block";
            }

            await applyAuthenticatedUI();
            handleRouting();
        } else {
            showLoginError(data.error || "Authentication failed. Check your credentials.");
        }
    } catch (err) {
        showLoginError("Network connection error: " + err.message);
    } finally {
        if (elements.btnLoginSubmit) elements.btnLoginSubmit.disabled = false;
        if (elements.loginBtnText) elements.loginBtnText.textContent = "Sign In";
        if (elements.loginBtnSpinner) elements.loginBtnSpinner.classList.add("hidden");
    }
}

function showLoginError(msg) {
    if (elements.loginErrorBanner) {
        elements.loginErrorBanner.innerHTML = `<span>⚠️</span> <span>${escapeHtml(msg)}</span>`;
        elements.loginErrorBanner.classList.remove("hidden");
    }
}

async function handleLogout() {
    try {
        if (authState.token) {
            await fetch(`${API_BASE}/api/auth/logout`, {
                method: "POST",
                headers: { "Authorization": `Bearer ${authState.token}` }
            });
        }
    } catch (e) {
        console.error("Logout request error:", e);
    }
    history.replaceState(null, "", "#/login");
    handleUnauthorized();
    showToast("You have been signed out.", "info");
}

window.quickFillAccount = function(username, password, role) {
    if (elements.loginUsername) elements.loginUsername.value = username;
    if (elements.loginPassword) elements.loginPassword.value = password;
    if (elements.loginSelectedRole) elements.loginSelectedRole.value = role;

    document.querySelectorAll(".role-pill").forEach(btn => {
        if (btn.dataset.role === role) {
            btn.classList.add("active");
        } else {
            btn.classList.remove("active");
        }
    });

    updateLoginFormForRole(role);
    if (elements.loginErrorBanner) elements.loginErrorBanner.classList.add("hidden");
};

// ----------------------------------------------------------------------------
// DATA LOADING
// ----------------------------------------------------------------------------
async function loadInitialData() {
    try {
        const [studentsRes, coursesRes] = await Promise.all([
            fetch(`${API_BASE}/api/students`),
            fetch(`${API_BASE}/api/courses`)
        ]);

        state.students = await studentsRes.json();
        state.courses = await coursesRes.json();

        populateStudentSelector();
        if (state.students.length > 0) {
            state.currentStudentId = state.students[0].studentId;
            await loadStudentData(state.currentStudentId);
        }
    } catch (err) {
        showToast("Error loading system data: " + err.message, "error");
    }
}

async function loadScenarios() {
    try {
        const res = await fetch(`${API_BASE}/api/scenarios`);
        state.scenarios = await res.json();
        renderScenariosToolbar();
    } catch (err) {
        console.error("Failed to load viva scenarios:", err);
    }
}

async function loadFaculty() {
    try {
        const res = await fetch(`${API_BASE}/api/registrar/faculty`);
        state.faculty = await res.json();
    } catch (err) {
        console.error("Failed to load faculty:", err);
    }
}

function renderScenariosToolbar() {
    if (!elements.scenariosButtonGrid || !state.scenarios) return;

    elements.scenariosButtonGrid.innerHTML = state.scenarios.map(sc => {
        let tagClass = "tag-valid";
        if (sc.badge === "PREREQ_VIOLATION") tagClass = "tag-prereq";
        else if (sc.badge === "TIMETABLE_CLASH") tagClass = "tag-clash";
        else if (sc.badge === "HIGH_RISK") tagClass = "tag-high";
        else if (sc.badge === "MEDIUM_RISK") tagClass = "tag-med";
        else if (sc.badge === "LOW_RISK") tagClass = "tag-low";

        return `
            <button class="scenario-btn" onclick="activateScenario(${sc.id})" title="${escapeHtml(sc.description)}">
                <div class="scenario-btn-top">
                    <span class="scenario-btn-title">Scenario ${sc.id}</span>
                    <span class="scenario-tag ${tagClass}">${sc.badge.replace(/_/g, " ")}</span>
                </div>
                <div class="scenario-btn-desc">${escapeHtml(sc.name.split(":")[1] || sc.name)}</div>
            </button>
        `;
    }).join("");
}

async function activateScenario(scenarioId) {
    const sc = state.scenarios.find(s => s.id === scenarioId);
    if (!sc) return;

    showToast(`Loading ${sc.name}...`, "info");

    if (sc.type === "REGISTRATION") {
        switchTab("student-tab");
        state.currentStudentId = sc.studentId;
        elements.studentSelector.value = sc.studentId;
        await loadStudentData(sc.studentId);

        // Highlight target course card
        setTimeout(() => {
            const course = state.courses.find(c => c.courseId === sc.courseId);
            if (!course) return;

            const card = document.querySelector(`.course-card[data-course-id="${sc.courseId}"]`);
            if (card) {
                card.scrollIntoView({ behavior: "smooth", block: "center" });
                card.style.outline = "3px solid #3b82f6";
                card.style.boxShadow = "0 0 20px rgba(59, 130, 246, 0.6)";
                setTimeout(() => {
                    card.style.outline = "";
                    card.style.boxShadow = "";
                }, 4000);
            }

            // If Scenario 1, trigger registration prompt
            if (scenarioId === 1) {
                promptEnrollment(course.courseId, course.courseCode, course.courseName);
            }
        }, 300);
    } else if (sc.type === "RISK") {
        switchTab("registrar-tab");
        await loadRegistrarDashboard();

        setTimeout(() => {
            const rows = document.querySelectorAll("#advisor-alerts-tbody tr");
            rows.forEach(row => {
                if (row.dataset.studentId == sc.studentId) {
                    row.scrollIntoView({ behavior: "smooth", block: "center" });
                    row.style.backgroundColor = "rgba(239, 68, 68, 0.25)";
                    setTimeout(() => {
                        row.style.backgroundColor = "";
                    }, 4000);
                }
            });
        }, 300);
    }
}

function populateStudentSelector() {
    elements.studentSelector.innerHTML = "";
    state.students.forEach(s => {
        const opt = document.createElement("option");
        opt.value = s.studentId;
        opt.textContent = `${s.name} (${s.rollNumber}) - GPA: ${s.gpa.toFixed(2)}`;
        elements.studentSelector.appendChild(opt);
    });
}

function populateAdminDropdowns() {
    if (!elements.adminPrereqTarget) return;

    const courseOptions = state.courses.map(c => 
        `<option value="${c.courseId}">${c.courseCode} - ${escapeHtml(c.courseName)}</option>`
    ).join("");

    elements.adminPrereqTarget.innerHTML = courseOptions;
    elements.adminPrereqSource.innerHTML = courseOptions;
    elements.adminSlotCourse.innerHTML = courseOptions;

    if (state.faculty && elements.adminSlotFaculty) {
        elements.adminSlotFaculty.innerHTML = state.faculty.map(f =>
            `<option value="${f.facultyId}">${escapeHtml(f.name)} (${f.department})</option>`
        ).join("");
    }
}

async function loadStudentData(studentId) {
    try {
        const res = await fetch(`${API_BASE}/api/student-details?studentId=${studentId}`);
        state.studentDetails = await res.json();

        renderStudentProfile();
        renderStudentSchedule();
        await validateAllCoursesForCurrentStudent();
        renderCourseCatalog();
        updateDashboardStats();
    } catch (err) {
        showToast("Failed to fetch student details: " + err.message, "error");
    }
}

// ----------------------------------------------------------------------------
// RENDERING STUDENT PORTAL
// ----------------------------------------------------------------------------
function renderStudentProfile() {
    const s = state.studentDetails;
    if (!s) return;

    const riskBadgeClass = s.riskFlag === "HIGH" ? "badge-danger" : (s.riskFlag === "MEDIUM" ? "badge-warning" : "badge-success");

    elements.studentMetaCard.innerHTML = `
        <div class="meta-item">
            <span class="meta-label">Roll Number</span>
            <span class="meta-value">${escapeHtml(s.rollNumber)}</span>
        </div>
        <div class="meta-item">
            <span class="meta-label">Cumulative GPA</span>
            <span class="meta-value">${s.gpa.toFixed(2)} / 4.00</span>
        </div>
        <div class="meta-item">
            <span class="meta-label">Attendance</span>
            <span class="meta-value">${s.attendancePct.toFixed(1)}%</span>
        </div>
        <div class="meta-item">
            <span class="meta-label">Failed Courses</span>
            <span class="meta-value" style="color: ${s.failedCourses > 0 ? '#f87171' : 'var(--text-primary)'}; font-weight: 600;">
                ${s.failedCourses || 0}
            </span>
        </div>
        <div class="meta-item">
            <span class="meta-label">Registered Credits</span>
            <span class="meta-value">${s.currentCredits || 0} / 18 Max</span>
        </div>
        <div class="meta-item">
            <span class="meta-label">Academic Risk Status</span>
            <span class="meta-value"><span class="badge ${riskBadgeClass}">${s.riskFlag} RISK (${s.riskScore.toFixed(3)})</span></span>
        </div>
    `;
}

function renderStudentSchedule() {
    const s = state.studentDetails;
    if (!s) return;

    // Active enrollments
    const active = s.enrollments.filter(e => e.status === "ENROLLED");
    elements.enrolledCountBadge.textContent = `${active.length} Enrolled`;

    if (active.length === 0) {
        elements.currentEnrollmentsList.innerHTML = `<div class="text-muted" style="padding: 1rem; text-align: center; color: var(--text-muted);">No courses currently enrolled for Spring 2026. Browse catalog below!</div>`;
    } else {
        elements.currentEnrollmentsList.innerHTML = active.map(e => `
            <div class="enrollment-item">
                <div class="item-left">
                    <span class="item-code">${escapeHtml(e.courseCode)}</span>
                    <span class="item-name">${escapeHtml(e.courseName)}</span>
                </div>
                <div class="item-right">
                    <span class="badge badge-info">Active</span>
                    <button class="btn btn-danger btn-sm" onclick="dropCourse(${e.enrollmentId}, '${escapeHtml(e.courseCode)}')">Drop</button>
                </div>
            </div>
        `).join("");
    }

    // Completed courses
    const completed = s.enrollments.filter(e => e.status === "COMPLETED");
    elements.completedCountBadge.textContent = `${completed.length} Passed`;

    if (completed.length === 0) {
        elements.completedCoursesList.innerHTML = `<div class="text-muted" style="padding: 1rem; text-align: center; color: var(--text-muted);">No prior completed courses on record.</div>`;
    } else {
        elements.completedCoursesList.innerHTML = completed.map(e => `
            <div class="transcript-item">
                <div class="item-left">
                    <span class="item-code">${escapeHtml(e.courseCode)}</span>
                    <span class="item-name">${escapeHtml(e.courseName)}</span>
                </div>
                <div class="item-right">
                    <span class="badge badge-success">Grade: ${e.grade}</span>
                </div>
            </div>
        `).join("");
    }
}

async function validateAllCoursesForCurrentStudent() {
    state.validations = {};
    const promises = state.courses.map(async (c) => {
        try {
            const res = await fetch(`${API_BASE}/api/validate?studentId=${state.currentStudentId}&courseId=${c.courseId}`);
            state.validations[c.courseId] = await res.json();
        } catch (e) {
            state.validations[c.courseId] = { eligible: false, error: e.message };
        }
    });
    await Promise.all(promises);
}

function renderCourseCatalog() {
    const s = state.studentDetails;
    if (!s) return;

    const enrolledCourseIds = new Set(s.enrollments.filter(e => e.status === "ENROLLED").map(e => e.courseId));
    const completedCourseIds = new Set(s.enrollments.filter(e => e.status === "COMPLETED").map(e => e.courseId));

    elements.courseCatalogGrid.innerHTML = state.courses.map(c => {
        const val = state.validations[c.courseId] || { eligible: false };
        const isEnrolled = enrolledCourseIds.has(c.courseId);
        const isCompleted = completedCourseIds.has(c.courseId);

        let cardClass = "";
        let badgeHtml = "";
        let noticeHtml = "";
        let actionBtnHtml = "";

        if (isCompleted) {
            cardClass = "status-eligible";
            badgeHtml = `<span class="badge badge-success">COMPLETED</span>`;
            noticeHtml = `<div class="validation-notice notice-eligible">✓ Course passed in a prior term with grade credit</div>`;
            actionBtnHtml = `<button class="btn btn-secondary" disabled>Already Passed</button>`;
        } else if (isEnrolled) {
            cardClass = "status-enrolled";
            badgeHtml = `<span class="badge badge-info">ENROLLED</span>`;
            noticeHtml = `<div class="validation-notice notice-info">ℹ Active in Spring 2026 Schedule</div>`;
            const enrollmentRecord = s.enrollments.find(e => e.courseId === c.courseId && e.status === "ENROLLED");
            actionBtnHtml = `<button class="btn btn-danger" onclick="dropCourse(${enrollmentRecord ? enrollmentRecord.enrollmentId : 0}, '${c.courseCode}')">Drop Course</button>`;
        } else if (val.hasTimetableClash) {
            cardClass = "status-clash";
            badgeHtml = `<span class="badge badge-danger">TIMETABLE CLASH</span>`;
            noticeHtml = `<div class="validation-notice notice-danger">⚠️ Schedule Conflict: ${escapeHtml(val.clashDetails)}</div>`;
            actionBtnHtml = `<button class="btn btn-primary" disabled title="Timetable clash with existing enrollment">Clash Detected</button>`;
        } else if (!val.prerequisitesMet) {
            cardClass = "status-missing-prereq";
            badgeHtml = `<span class="badge badge-warning">PREREQ REQUIRED</span>`;
            const missingText = val.missingPrerequisites.join(", ");
            noticeHtml = `<div class="validation-notice notice-warning">⚠️ Prerequisite Unmet: Requires ${escapeHtml(missingText)}</div>`;
            actionBtnHtml = `<button class="btn btn-primary" disabled title="Prerequisites not completed">Prereq Missing</button>`;
        } else if (val.creditLimitExceeded) {
            cardClass = "status-clash";
            badgeHtml = `<span class="badge badge-danger">REGISTRATION BLOCKED</span>`;
            noticeHtml = `<div class="validation-notice notice-danger">⚠️ ${escapeHtml(val.failureReason)}</div>`;
            actionBtnHtml = `<button class="btn btn-primary" disabled>Credit Exceeded</button>`;
        } else {
            cardClass = "status-eligible";
            badgeHtml = `<span class="badge badge-success">ELIGIBLE</span>`;
            noticeHtml = `<div class="validation-notice notice-eligible">✓ Prerequisites verified & no schedule clash! (${(s.currentCredits || 0) + c.credits} / 18 cr)</div>`;
            actionBtnHtml = `<button class="btn btn-primary" onclick="promptEnrollment(${c.courseId}, '${escapeHtml(c.courseCode)}', '${escapeHtml(c.courseName)}')">Enroll Now</button>`;
        }

        const slotPills = c.slots.map(sl => `
            <span class="slot-pill">${sl.dayOfWeek} ${sl.startTime}-${sl.endTime} (${sl.roomNumber})</span>
        `).join("");

        return `
            <div class="course-card ${cardClass}" data-course-id="${c.courseId}" data-course-code="${escapeHtml(c.courseCode)}" data-course-name="${escapeHtml(c.courseName)}" data-dept="${escapeHtml(c.department)}">
                <div>
                    <div class="course-header">
                        <span class="course-code">${escapeHtml(c.courseCode)}</span>
                        ${badgeHtml}
                    </div>
                    <h3 class="course-title">${escapeHtml(c.courseName)}</h3>
                    <p class="course-dept">${escapeHtml(c.department)} • ${c.credits} Credits</p>
                    <div class="slot-pills">
                        ${slotPills || '<span class="slot-pill">Online / TBA</span>'}
                    </div>
                    ${noticeHtml}
                </div>
                <div class="course-actions">
                    ${actionBtnHtml}
                </div>
            </div>
        `;
    }).join("");
}

function filterCourseCatalog(keyword) {
    document.querySelectorAll(".course-card").forEach(card => {
        const code = card.dataset.courseCode.toLowerCase();
        const name = card.dataset.courseName.toLowerCase();
        const dept = card.dataset.dept.toLowerCase();
        if (code.includes(keyword) || name.includes(keyword) || dept.includes(keyword)) {
            card.style.display = "flex";
        } else {
            card.style.display = "none";
        }
    });
}

// ----------------------------------------------------------------------------
// ENROLLMENT & DROP ACTIONS (5-STEP AUDIT MODAL)
// ----------------------------------------------------------------------------
function promptEnrollment(courseId, courseCode, courseName) {
    const s = state.studentDetails;
    const course = state.courses.find(c => c.courseId === courseId);
    const credits = course ? course.credits : 3;
    const currentCr = s.currentCredits || 0;
    const newTotal = currentCr + credits;

    elements.modalTitle.textContent = `Registration Audit: ${courseCode}`;
    elements.modalBodyContent.innerHTML = `
        <p>Pre-registration validation audit for <strong>${escapeHtml(s.name)}</strong> (Roll: ${escapeHtml(s.rollNumber)}):</p>
        <div style="background-color: var(--bg-input); padding: 0.9rem; border-radius: var(--radius-md); margin: 0.8rem 0; border: 1px solid var(--border-color);">
            <strong style="color: var(--primary); font-family: 'JetBrains Mono', monospace; font-size: 1.05rem;">${escapeHtml(courseCode)}: ${escapeHtml(courseName)}</strong><br>
            <span style="font-size: 0.82rem; color: var(--text-secondary);">Course Credits: ${credits} • Academic Term: Spring 2026</span>
        </div>

        <div class="audit-step-list">
            <div class="audit-step-item">
                <span class="audit-step-icon audit-pass">✓</span>
                <div>
                    <strong>Step 1: Prerequisite DAG Invariant Verification</strong>
                    <div style="font-size: 0.78rem; color: var(--text-muted);">All directed prerequisite edges verified completed in student academic transcript.</div>
                </div>
            </div>
            <div class="audit-step-item">
                <span class="audit-step-icon audit-pass">✓</span>
                <div>
                    <strong>Step 2: Duplicate Enrollment Verification</strong>
                    <div style="font-size: 0.78rem; color: var(--text-muted);">Student has no active prior registration for ${escapeHtml(courseCode)} in Spring 2026.</div>
                </div>
            </div>
            <div class="audit-step-item">
                <span class="audit-step-icon audit-pass">✓</span>
                <div>
                    <strong>Step 3: Timetable Clash Collision Check</strong>
                    <div style="font-size: 0.78rem; color: var(--text-muted);">Augmented Interval Tree confirmed zero slot overlap: (s1 &lt; e2 AND s2 &lt; e1) evaluates to FALSE.</div>
                </div>
            </div>
            <div class="audit-step-item">
                <span class="audit-step-icon audit-pass">✓</span>
                <div>
                    <strong>Step 4: Academic Credit Limit Verification</strong>
                    <div style="font-size: 0.78rem; color: var(--text-muted);">New total load: ${currentCr} + ${credits} = <strong>${newTotal} credits</strong> (Threshold: Maximum 18 credits).</div>
                </div>
            </div>
            <div class="audit-step-item">
                <span class="audit-step-icon audit-pass">✓</span>
                <div>
                    <strong>Step 5: Authorization & ACID Commit</strong>
                    <div style="font-size: 0.78rem; color: var(--text-muted);">Registration approved by Registrar module. Ready to commit to registration.db.</div>
                </div>
            </div>
        </div>
    `;

    elements.modalConfirmBtn.onclick = () => executeEnrollment(courseId);
    showModal();
}

async function executeEnrollment(courseId) {
    hideModal();
    try {
        const res = await authFetch(`${API_BASE}/api/enroll`, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({
                studentId: state.currentStudentId,
                courseId: courseId,
                semester: "Spring 2026"
            })
        });

        const data = await res.json();
        if (data.success) {
            showToast(data.message || "✓ Registration Successful", "success");
            await loadStudentData(state.currentStudentId);
            if (state.activeTab === "registrar-tab") {
                loadRegistrarDashboard();
            }
        } else {
            showRegistrationBlockedModal(data.heading, data.reason, data.details);
            showToast(`Registration blocked: ${data.reason || data.error}`, "error");
        }
    } catch (err) {
        showToast("Network error: " + err.message, "error");
    }
}

function showRegistrationBlockedModal(heading, reason, details) {
    elements.modalTitle.textContent = heading || "Registration blocked";
    elements.modalBodyContent.innerHTML = `
        <div style="background-color: rgba(239, 68, 68, 0.12); border: 1px solid var(--danger); border-radius: var(--radius-md); padding: 1.25rem; margin-bottom: 1rem;">
            <div style="display: flex; align-items: center; gap: 0.75rem; margin-bottom: 0.75rem;">
                <span style="font-size: 1.5rem;">❌</span>
                <h4 style="color: #fca5a5; font-size: 1.15rem; margin: 0; font-weight: 700;">${escapeHtml(heading || "Registration blocked")}</h4>
            </div>
            <p style="font-size: 0.95rem; font-weight: 600; color: #ffffff; margin-bottom: 0.6rem;">
                <strong>Reason:</strong> ${escapeHtml(reason || "Registration requirements not satisfied.")}
            </p>
            ${details ? `
                <div style="margin-top: 0.5rem;">
                    <span style="font-size: 0.78rem; text-transform: uppercase; color: var(--text-muted); font-weight: 700; letter-spacing: 0.05em; display: block; margin-bottom: 0.35rem;">Audit Details:</span>
                    <div style="background: rgba(0,0,0,0.35); padding: 0.85rem; border-radius: var(--radius-sm); font-family: 'JetBrains Mono', monospace; font-size: 0.85rem; color: #fecaca; white-space: pre-wrap; line-height: 1.5; border: 1px solid rgba(239,68,68,0.2);">${escapeHtml(details)}</div>
                </div>
            ` : ""}
        </div>
    `;
    if (elements.modalFooterContent) {
        elements.modalFooterContent.innerHTML = `
            <button class="btn btn-secondary" onclick="hideModal()">Dismiss</button>
        `;
    }
    showModal();
}

async function dropCourse(enrollmentId, courseCode) {
    if (!confirm(`Are you sure you want to drop course ${courseCode}?`)) return;

    try {
        const res = await authFetch(`${API_BASE}/api/drop`, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ enrollmentId })
        });
        const data = await res.json();
        if (data.success) {
            showToast(`Course ${courseCode} dropped successfully.`, "info");
            await loadStudentData(state.currentStudentId);
            if (state.activeTab === "registrar-tab") {
                loadRegistrarDashboard();
            }
        } else {
            showToast("Drop failed: " + data.error, "error");
        }
    } catch (err) {
        showToast("Drop error: " + err.message, "error");
    }
}

// ----------------------------------------------------------------------------
// REGISTRAR & ADVISOR DASHBOARD
// ----------------------------------------------------------------------------
async function loadRegistrarDashboard() {
    try {
        const [studentsRes, enrollmentsRes, atRiskRes] = await Promise.all([
            fetch(`${API_BASE}/api/students`),
            fetch(`${API_BASE}/api/enrollments`),
            fetch(`${API_BASE}/api/at-risk`)
        ]);

        const students = await studentsRes.json();
        const enrollments = await enrollmentsRes.json();
        const atRisk = await atRiskRes.json();

        // Update KPIs
        if (elements.kpiTotalStudents) elements.kpiTotalStudents.textContent = students.length;
        if (elements.kpiTotalCourses) elements.kpiTotalCourses.textContent = state.courses.length;

        const highRisk = students.filter(s => s.riskFlag === "HIGH");
        const medRisk = students.filter(s => s.riskFlag === "MEDIUM");
        const lowRisk = students.filter(s => s.riskFlag === "LOW");

        if (elements.kpiHighRisk) elements.kpiHighRisk.textContent = highRisk.length;
        if (elements.kpiMedRisk) elements.kpiMedRisk.textContent = medRisk.length;
        if (elements.kpiLowRisk) elements.kpiLowRisk.textContent = lowRisk.length;

        if (elements.totalEnrollmentsBadge) {
            elements.totalEnrollmentsBadge.textContent = `${enrollments.length} Total Records`;
        }

        // Sync KPI filter card active styles
        document.querySelectorAll("#registrar-tab .kpi-filter-card").forEach(card => {
            const r = card.dataset.risk;
            if (r === state.riskFilter) {
                card.classList.add("active-filter");
            } else {
                card.classList.remove("active-filter");
            }
        });

        // Sync filter button bar active styles
        document.querySelectorAll("#advisor-risk-filter-group .btn-filter-risk").forEach(btn => {
            if (btn.dataset.risk === state.riskFilter) {
                btn.classList.add("active");
            } else {
                btn.classList.remove("active");
            }
        });

        // Update advisor filter status label & reset button
        if (elements.advisorFilterStatus) {
            const countLabel = state.riskFilter === "ALL" 
                ? `All Students (${students.length})` 
                : `${state.riskFilter} Risk Cohort`;
            elements.advisorFilterStatus.innerHTML = `Showing: <strong style="color: var(--primary);">${countLabel}</strong>`;
        }
        if (elements.btnResetAdvisorFilter) {
            elements.btnResetAdvisorFilter.style.display = state.riskFilter === "ALL" ? "none" : "inline-block";
        }

        // Filter students according to selected scenario
        let displayList = students;
        if (state.riskFilter === "HIGH") {
            displayList = students.filter(s => s.riskFlag === "HIGH");
        } else if (state.riskFilter === "MEDIUM") {
            displayList = students.filter(s => s.riskFlag === "MEDIUM");
        } else if (state.riskFilter === "LOW") {
            displayList = students.filter(s => s.riskFlag === "LOW");
        }

        // Render Advisor Alerts Table
        if (!elements.advisorAlertsTbody) return;

        if (displayList.length === 0) {
            const riskName = state.riskFilter === "ALL" ? "academic risk" : `${state.riskFilter} risk`;
            elements.advisorAlertsTbody.innerHTML = `<tr><td colspan="8" style="text-align: center; color: var(--text-muted); padding: 1.5rem;">No students found matching <strong>${riskName}</strong> criteria.</td></tr>`;
        } else {
            elements.advisorAlertsTbody.innerHTML = displayList.map(s => {
                const badgeClass = s.riskFlag === "HIGH" ? "badge-danger" : (s.riskFlag === "MEDIUM" ? "badge-warning" : "badge-success");
                const actionPlan = s.riskFlag === "HIGH" 
                    ? `<span style="color: #f87171; font-weight: 600;">🚨 Academic counseling recommended & review credit load</span>` 
                    : (s.riskFlag === "MEDIUM" 
                        ? `<span style="color: #fbbf24;">⚠️ Bi-weekly tutoring & attendance monitoring recommended</span>`
                        : `<span style="color: #34d399;">✅ Good standing. Maintain current course progression</span>`);

                return `
                    <tr data-student-id="${s.studentId}">
                        <td><strong>${s.studentId}</strong></td>
                        <td style="font-family: 'JetBrains Mono', monospace;">${escapeHtml(s.rollNumber)}</td>
                        <td>${escapeHtml(s.name)}</td>
                        <td><strong>${s.gpa.toFixed(2)}</strong></td>
                        <td>${s.attendancePct.toFixed(1)}%</td>
                        <td style="font-family: 'JetBrains Mono', monospace;">${(s.riskScore || 0).toFixed(3)}</td>
                        <td><span class="badge ${badgeClass}">${s.riskFlag}</span></td>
                        <td>${actionPlan}</td>
                    </tr>
                `;
            }).join("");
        }

        // Render Master Enrollments Table
        elements.masterEnrollmentsTbody.innerHTML = enrollments.map(e => `
            <tr>
                <td>#${e.enrollmentId}</td>
                <td><strong>${escapeHtml(e.studentName)}</strong> (ID ${e.studentId})</td>
                <td style="font-family: 'JetBrains Mono', monospace; color: var(--primary);">${escapeHtml(e.courseCode)}</td>
                <td>${escapeHtml(e.courseName)}</td>
                <td>${escapeHtml(e.semester)}</td>
                <td>
                    <span class="badge ${e.status === 'ENROLLED' ? 'badge-info' : (e.status === 'COMPLETED' ? 'badge-success' : 'badge-danger')}">
                        ${escapeHtml(e.status)}
                    </span>
                </td>
                <td><strong>${e.grade}</strong></td>
                <td>
                    ${e.status === 'ENROLLED' ? `<button class="btn btn-danger btn-sm" onclick="dropCourse(${e.enrollmentId}, '${escapeHtml(e.courseCode)}')">Drop</button>` : '-'}
                </td>
            </tr>
        `).join("");
    } catch (err) {
        showToast("Error loading registrar dashboard: " + err.message, "error");
    }
}

async function runRiskPipeline() {
    elements.btnRunRiskPipeline.disabled = true;
    elements.btnRunRiskPipeline.innerHTML = `<span class="btn-icon">⏳</span> Computing Risk Scores...`;

    try {
        const res = await fetch(`${API_BASE}/api/run-risk-pipeline`, { method: "POST" });
        const data = await res.json();
        if (data.success) {
            showToast("Layer 3 Python statistical pipeline executed! Academic risk indicators updated.", "success");
            await loadRegistrarDashboard();
            await loadStudentData(state.currentStudentId);
        } else {
            showToast("Risk pipeline execution failed: " + data.error, "error");
        }
    } catch (err) {
        showToast("Error triggering pipeline: " + err.message, "error");
    } finally {
        elements.btnRunRiskPipeline.disabled = false;
        elements.btnRunRiskPipeline.innerHTML = `<span class="btn-icon">⚡</span> Run ML Risk Assessment`;
    }
}

// ----------------------------------------------------------------------------
// REGISTRAR MANAGEMENT HANDLERS (ADMIN)
// ----------------------------------------------------------------------------
async function handleAddCourse(e) {
    e.preventDefault();
    const code = document.getElementById("admin-course-code").value.trim();
    const name = document.getElementById("admin-course-name").value.trim();
    const credits = parseInt(document.getElementById("admin-course-credits").value);
    const department = document.getElementById("admin-course-dept").value.trim();

    try {
        const res = await fetch(`${API_BASE}/api/registrar/course/add`, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ courseCode: code, courseName: name, credits: credits, department: department })
        });
        const data = await res.json();
        if (data.success) {
            showToast(data.message, "success");
            document.getElementById("form-add-course").reset();
            // Refresh courses
            const cRes = await fetch(`${API_BASE}/api/courses`);
            state.courses = await cRes.json();
            populateAdminDropdowns();
            renderCourseCatalog();
        } else {
            showToast("Failed to create course: " + data.error, "error");
        }
    } catch (err) {
        showToast("Error creating course: " + err.message, "error");
    }
}

async function handleAddPrereq(e) {
    e.preventDefault();
    const targetCourseId = parseInt(elements.adminPrereqTarget.value);
    const prereqCourseId = parseInt(elements.adminPrereqSource.value);

    if (targetCourseId === prereqCourseId) {
        showToast("A course cannot be a prerequisite of itself.", "error");
        return;
    }

    try {
        const res = await fetch(`${API_BASE}/api/registrar/prerequisite/add`, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ courseId: targetCourseId, prerequisiteId: prereqCourseId })
        });
        const data = await res.json();
        if (data.success) {
            showToast(data.message, "success");
            // Refresh DAG and validations
            const cRes = await fetch(`${API_BASE}/api/courses`);
            state.courses = await cRes.json();
            await validateAllCoursesForCurrentStudent();
            renderCourseCatalog();
            if (state.activeTab === "dag-tab") {
                renderDagVisualizer();
            }
        } else {
            showToast(data.error, "error");
        }
    } catch (err) {
        showToast("Error linking prerequisite: " + err.message, "error");
    }
}

async function handleAddSlot(e) {
    e.preventDefault();
    const courseId = parseInt(elements.adminSlotCourse.value);
    const day = document.getElementById("admin-slot-day").value;
    const room = document.getElementById("admin-slot-room").value.trim();
    const start = document.getElementById("admin-slot-start").value.trim();
    const end = document.getElementById("admin-slot-end").value.trim();
    const facultyId = parseInt(elements.adminSlotFaculty.value);

    try {
        const res = await fetch(`${API_BASE}/api/registrar/slot/add`, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({
                courseId: courseId,
                dayOfWeek: day,
                startTime: start,
                endTime: end,
                roomNumber: room,
                facultyId: facultyId
            })
        });
        const data = await res.json();
        if (data.success) {
            showToast(data.message, "success");
            const cRes = await fetch(`${API_BASE}/api/courses`);
            state.courses = await cRes.json();
            await validateAllCoursesForCurrentStudent();
            renderCourseCatalog();
        } else {
            showToast("Failed to assign slot: " + data.error, "error");
        }
    } catch (err) {
        showToast("Error scheduling slot: " + err.message, "error");
    }
}

async function handleAddStudent(e) {
    e.preventDefault();
    const name = document.getElementById("admin-student-name").value.trim();
    const email = document.getElementById("admin-student-email").value.trim();
    const roll = document.getElementById("admin-student-roll").value.trim();
    const gpa = parseFloat(document.getElementById("admin-student-gpa").value);
    const attendancePct = parseFloat(document.getElementById("admin-student-att").value);
    const failedCourses = parseInt(document.getElementById("admin-student-fails").value);

    const passwordInput = document.getElementById("admin-student-password");
    const rawPassword = passwordInput ? passwordInput.value.trim() : "";

    if (!name) {
        showToast("Student name is required.", "error");
        return;
    }

    try {
        const btn = document.getElementById("btn-submit-student");
        if (btn) {
            btn.disabled = true;
            btn.textContent = "Registering & Provisioning...";
        }

        const res = await authFetch(`${API_BASE}/api/student/add`, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({
                name: name,
                email: email,
                rollNumber: roll,
                password: rawPassword,
                gpa: gpa,
                attendancePct: attendancePct,
                failedCourses: failedCourses
            })
        });
        const data = await res.json();
        if (data.success) {
            showToast(`Student #${data.studentId} registered successfully!`, "success");

            // Display created credentials for staff/admin to hand to the student
            const alertBox = document.getElementById("admin-student-created-alert");
            if (alertBox) {
                alertBox.innerHTML = `
                    <div style="font-weight: 700; color: #10b981; margin-bottom: 0.35rem; display: flex; align-items: center; gap: 0.4rem;">
                        <span>✓</span> Student Login Account Provisioned
                    </div>
                    <div style="font-size: 0.85rem; color: var(--text-primary); line-height: 1.5;">
                        Provide these credentials to <strong>${escapeHtml(data.name || name)}</strong>:<br>
                        <strong>Roll Number (Login ID):</strong> <code style="background: var(--bg-card); padding: 0.15rem 0.4rem; border-radius: 4px; border: 1px solid var(--border-color); color: var(--primary);">${escapeHtml(data.rollNumber)}</code><br>
                        <strong>Password:</strong> <code style="background: var(--bg-card); padding: 0.15rem 0.4rem; border-radius: 4px; border: 1px solid var(--border-color); color: var(--primary);">${escapeHtml(data.password || rawPassword || "Student@2026")}</code>
                    </div>
                `;
                alertBox.classList.remove("hidden");
                alertBox.style.display = "block";
            }

            document.getElementById("form-add-student").reset();
            document.getElementById("admin-student-gpa").value = "3.50";
            document.getElementById("admin-student-att").value = "90.0";
            document.getElementById("admin-student-fails").value = "0";

            // Refresh student list across the app
            const sRes = await fetch(`${API_BASE}/api/students`);
            state.students = await sRes.json();
            populateStudentSelector();
            elements.studentSelector.value = data.studentId;
            await loadStudentData(data.studentId);
            if (state.activeTab === "registrar-tab") {
                await loadRegistrarDashboard();
            } else if (state.activeTab === "admin-tab") {
                await loadAdminDashboard();
            }
        } else {
            showToast("Failed to add student: " + (data.error || "Unknown error"), "error");
        }
    } catch (err) {
        showToast("Error adding student: " + err.message, "error");
    } finally {
        const btn = document.getElementById("btn-submit-student");
        if (btn) {
            btn.disabled = false;
            btn.textContent = "Register Student & Provision Login";
        }
    }
}

// ----------------------------------------------------------------------------
// PREREQUISITE DAG VISUALIZER
// ----------------------------------------------------------------------------
async function renderDagVisualizer() {
    try {
        const res = await fetch(`${API_BASE}/api/dag?studentId=${state.currentStudentId}`);
        const dag = await res.json();

        // Cycle Alert Display
        if (dag.hasCycle) {
            elements.dagCycleAlert.style.display = "block";
            elements.dagCycleAlert.innerHTML = escapeHtml(dag.cycleDetails);
        } else {
            elements.dagCycleAlert.style.display = "none";
        }

        // Topological Sort Sequence Display
        if (dag.topologicalSort && dag.topologicalSort.length > 0) {
            elements.dagTopoOrder.innerHTML = dag.topologicalSort.map((cId, idx) => {
                const node = dag.nodes.find(n => n.id === cId);
                const label = node ? node.label : `Course ${cId}`;
                return `
                    <span class="topo-pill">${label}</span>
                    ${idx < dag.topologicalSort.length - 1 ? '<span class="topo-arrow">→</span>' : ''}
                `;
            }).join("");
        }

        // Compute tiers (topological levels)
        const inPrereqs = {};
        const outDependents = {};
        dag.nodes.forEach(n => {
            inPrereqs[n.id] = [];
            outDependents[n.id] = [];
        });

        dag.edges.forEach(e => {
            if (inPrereqs[e.to]) inPrereqs[e.to].push(e.from);
            if (outDependents[e.from]) outDependents[e.from].push(e.to);
        });

        // Compute longest path from roots for tier assignment
        const tiers = {};
        function getTier(nodeId) {
            if (tiers[nodeId] !== undefined) return tiers[nodeId];
            const prereqs = inPrereqs[nodeId] || [];
            if (prereqs.length === 0) {
                tiers[nodeId] = 0;
                return 0;
            }
            let maxT = 0;
            for (let p of prereqs) {
                maxT = Math.max(maxT, getTier(p) + 1);
            }
            tiers[nodeId] = maxT;
            return maxT;
        }

        dag.nodes.forEach(n => getTier(n.id));

        // Group nodes by tier
        const tierGroups = {};
        dag.nodes.forEach(n => {
            const t = tiers[n.id];
            if (!tierGroups[t]) tierGroups[t] = [];
            tierGroups[t].push(n);
        });

        const tierNames = [
            "Tier 0: Foundational Roots (No Prerequisites Required)",
            "Tier 1: Intermediate Foundations (Requires Tier 0)",
            "Tier 2: Advanced Core (Requires Tier 1)",
            "Tier 3: Specialization & Capstone (Requires Tier 2)"
        ];

        let html = "";
        for (let t = 0; t <= 3; t++) {
            const nodes = tierGroups[t] || [];
            if (nodes.length === 0) continue;

            const nodeBoxes = nodes.map(n => {
                const reqs = inPrereqs[n.id].map(pId => {
                    const prereqNode = dag.nodes.find(x => x.id === pId);
                    return prereqNode ? prereqNode.label : `Course ${pId}`;
                }).join(", ");

                let statusClass = "";
                let statusLabel = "";
                if (n.status === "COMPLETED") {
                    statusClass = "status-completed";
                    statusLabel = "✓ Passed";
                } else if (n.status === "ENROLLED") {
                    statusClass = "status-enrolled";
                    statusLabel = "ℹ Enrolled";
                } else if (n.status === "ELIGIBLE") {
                    statusClass = "status-eligible";
                    statusLabel = "★ Eligible";
                } else if (n.status === "PREREQ REQUIRED") {
                    statusClass = "status-prereq-required";
                    statusLabel = "⚠️ Missing Prereqs";
                }

                return `
                    <div class="dag-node-box ${statusClass}">
                        <div style="display: flex; justify-content: space-between; align-items: center;">
                            <div class="node-code">${escapeHtml(n.label)}</div>
                            ${statusLabel ? `<span style="font-size: 0.7rem; font-weight: 700;">${statusLabel}</span>` : ''}
                        </div>
                        <div class="node-title">${escapeHtml(n.title)}</div>
                        <div class="node-prereq-list">
                            ${reqs ? `Prerequisites: <strong>${escapeHtml(reqs)}</strong>` : `<em>Root Foundational Course</em>`}
                        </div>
                    </div>
                `;
            }).join("");

            html += `
                <div class="dag-tier">
                    <div class="dag-tier-title">
                        <span class="legend-circle level-${t}"></span>
                        ${tierNames[t] || `Tier ${t}`} (${nodes.length} Courses)
                    </div>
                    <div class="dag-nodes-row">
                        ${nodeBoxes}
                    </div>
                </div>
            `;
        }

        elements.dagContainer.innerHTML = html;
    } catch (err) {
        showToast("Error loading DAG visualizer: " + err.message, "error");
    }
}

// ----------------------------------------------------------------------------
// EDIT STUDENT MODAL LOGIC
// ----------------------------------------------------------------------------
function showEditModal() {
    const s = state.studentDetails;
    if (!s) return;
    elements.editName.value = s.name;
    elements.editEmail.value = s.email;
    elements.editGpa.value = s.gpa;
    elements.editAttendance.value = s.attendancePct;
    if (elements.editFailedCourses) {
        elements.editFailedCourses.value = s.failedCourses || 0;
    }
    elements.editModalContainer.classList.remove("hidden");
}

function hideEditModal() {
    elements.editModalContainer.classList.add("hidden");
}

async function saveStudentProfile() {
    const s = state.studentDetails;
    if (!s) return;

    const updatedName = elements.editName.value.trim();
    const updatedEmail = elements.editEmail.value.trim();
    const updatedGpa = parseFloat(elements.editGpa.value);
    const updatedAttendance = parseFloat(elements.editAttendance.value);
    const updatedFailed = parseInt(elements.editFailedCourses ? elements.editFailedCourses.value : 0);

    if (!updatedName || !updatedEmail || isNaN(updatedGpa) || isNaN(updatedAttendance) || isNaN(updatedFailed)) {
        showToast("Please provide valid values for all fields.", "error");
        return;
    }

    if (updatedGpa < 0.0 || updatedGpa > 4.0) {
        showToast("GPA must be between 0.00 and 4.00", "error");
        return;
    }

    if (updatedAttendance < 0.0 || updatedAttendance > 100.0) {
        showToast("Attendance must be between 0% and 100%", "error");
        return;
    }

    try {
        elements.editModalSaveBtn.disabled = true;
        elements.editModalSaveBtn.textContent = "Saving...";

        const res = await fetch(`${API_BASE}/api/student/update`, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({
                studentId: s.studentId,
                name: updatedName,
                email: updatedEmail,
                gpa: updatedGpa,
                attendancePct: updatedAttendance,
                failedCourses: updatedFailed
            })
        });

        const data = await res.json();
        if (data.success) {
            showToast("Student profile updated & risk score recalculated!", "success");
            hideEditModal();
            // Refresh list of students to update selector text and reload active student
            const studentsRes = await fetch(`${API_BASE}/api/students`);
            state.students = await studentsRes.json();
            populateStudentSelector();
            elements.studentSelector.value = s.studentId;
            await loadStudentData(s.studentId);
            if (state.activeTab === "registrar-tab") {
                await loadRegistrarDashboard();
            }
        } else {
            showToast("Update failed: " + data.error, "error");
        }
    } catch (err) {
        showToast("Error updating student: " + err.message, "error");
    } finally {
        elements.editModalSaveBtn.disabled = false;
        elements.editModalSaveBtn.textContent = "Save Changes";
    }
}

// ----------------------------------------------------------------------------
// MODAL & TOAST HELPERS
// ----------------------------------------------------------------------------
function showModal() {
    elements.modalContainer.classList.remove("hidden");
}

function hideModal() {
    elements.modalContainer.classList.add("hidden");
}

function showToast(message, type = "info") {
    const toast = document.createElement("div");
    toast.className = `toast toast-${type}`;
    const icon = type === "success" ? "✓" : (type === "error" ? "✕" : "ℹ");
    toast.innerHTML = `<span>${icon}</span> <span>${escapeHtml(message)}</span>`;
    elements.toastContainer.appendChild(toast);

    setTimeout(() => {
        toast.style.opacity = "0";
        setTimeout(() => toast.remove(), 300);
    }, 4000);
}

function escapeHtml(str) {
    if (!str) return "";
    return String(str)
        .replace(/&/g, "&amp;")
        .replace(/</g, "&lt;")
        .replace(/>/g, "&gt;")
        .replace(/"/g, "&quot;")
        .replace(/'/g, "&#039;");
}

// ----------------------------------------------------------------------------
// ADMIN DASHBOARD MODULE
// ----------------------------------------------------------------------------
async function loadAdminDashboard() {
    await loadAdminOverview();
    const activeSubtab = document.querySelector(".admin-subtab.active");
    const subtabId = activeSubtab ? activeSubtab.dataset.subtab : "admin-subtab-users";
    switchAdminSubtab(subtabId);
}

function setupAdminSubtabs() {
    document.querySelectorAll(".admin-subtab").forEach(tab => {
        tab.addEventListener("click", () => {
            switchAdminSubtab(tab.dataset.subtab);
        });
    });
}

function switchAdminSubtab(subtabId) {
    document.querySelectorAll(".admin-subtab").forEach(t => t.classList.remove("active"));
    document.querySelectorAll(".admin-subtab-content").forEach(c => c.classList.remove("active"));

    const tabBtn = document.querySelector(`.admin-subtab[data-subtab="${subtabId}"]`);
    const tabContent = document.getElementById(subtabId);
    if (tabBtn) tabBtn.classList.add("active");
    if (tabContent) tabContent.classList.add("active");

    if (subtabId === "admin-subtab-users") {
        loadAdminUsers();
    } else if (subtabId === "admin-subtab-students") {
        loadAdminStudents();
    } else if (subtabId === "admin-subtab-courses") {
        loadAdminCourses();
    } else if (subtabId === "admin-subtab-timetable") {
        loadAdminTimetable();
    } else if (subtabId === "admin-subtab-reports") {
        loadAdminReports();
    }
}

async function loadAdminOverview() {
    try {
        const res = await authFetch(`${API_BASE}/api/admin/overview`);
        if (!res.ok) return;
        const stats = await res.json();
        if (elements.adminKpiStudents) elements.adminKpiStudents.textContent = stats.totalStudents || 0;
        if (elements.adminKpiCourses) elements.adminKpiCourses.textContent = stats.totalCourses || 0;
        if (elements.adminKpiRegistrations) elements.adminKpiRegistrations.textContent = stats.totalRegistrations || 0;
        if (elements.adminKpiHighRisk) elements.adminKpiHighRisk.textContent = stats.highRiskStudents || 0;
        if (elements.adminKpiUsers) elements.adminKpiUsers.textContent = stats.totalUsers || 0;
    } catch (err) {
        console.error("Failed to load admin overview:", err);
    }
}

async function loadAdminUsers() {
    try {
        const res = await authFetch(`${API_BASE}/api/admin/users`);
        if (!res.ok) return;
        const users = await res.json();
        if (!elements.adminUsersTbody) return;

        elements.adminUsersTbody.innerHTML = users.map(u => {
            const statusBadge = u.isActive
                ? `<span class="badge badge-success">ACTIVE</span>`
                : `<span class="badge badge-danger">INACTIVE</span>`;
            const toggleText = u.isActive ? "Deactivate" : "Activate";
            const toggleClass = u.isActive ? "btn-secondary" : "btn-primary";

            return `
                <tr>
                    <td style="font-family: monospace;">#${u.id}</td>
                    <td><strong>${escapeHtml(u.username)}</strong></td>
                    <td><span class="badge badge-info">${escapeHtml(u.role)}</span></td>
                    <td>${u.studentId ? `${escapeHtml(u.studentName || '')} (ID: ${u.studentId})` : '<span class="text-muted">-</span>'}</td>
                    <td>${statusBadge}</td>
                    <td style="font-size: 0.8rem; color: var(--text-muted);">${escapeHtml(u.createdAt ? u.createdAt.substring(0, 10) : '-')}</td>
                    <td>
                        <div style="display: flex; gap: 0.4rem;">
                            <button class="btn ${toggleClass} btn-sm" onclick="toggleUserStatus(${u.id}, ${!u.isActive})">${toggleText}</button>
                            <button class="btn btn-secondary btn-sm" onclick="openResetPwModal(${u.id}, '${escapeHtml(u.username)}')">Reset PW</button>
                            <button class="btn btn-danger btn-sm" onclick="deleteUser(${u.id}, '${escapeHtml(u.username)}')">Delete</button>
                        </div>
                    </td>
                </tr>
            `;
        }).join("");
    } catch (err) {
        console.error("Failed to load admin users:", err);
    }
}

window.toggleAddUserBox = function(forceShow) {
    if (!elements.adminAddUserBox) return;
    if (typeof forceShow === "boolean") {
        if (forceShow) elements.adminAddUserBox.classList.remove("hidden");
        else elements.adminAddUserBox.classList.add("hidden");
    } else {
        elements.adminAddUserBox.classList.toggle("hidden");
    }
};

window.handleAdminAddUser = async function(e) {
    e.preventDefault();
    const username = document.getElementById("admin-new-username").value.trim();
    const password = document.getElementById("admin-new-password").value.trim();
    const role = document.getElementById("admin-new-role").value;
    const studentId = document.getElementById("admin-new-student-id").value.trim();

    try {
        const res = await authFetch(`${API_BASE}/api/admin/users/add`, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ username, password, role, studentId })
        });
        const data = await res.json();
        if (data.success) {
            showToast(`User '${username}' created successfully.`, "success");
            toggleAddUserBox(false);
            document.getElementById("admin-add-user-form").reset();
            loadAdminUsers();
            loadAdminOverview();
        } else {
            showToast("Failed to create user: " + data.error, "error");
        }
    } catch (err) {
        showToast("Error: " + err.message, "error");
    }
};

window.toggleUserStatus = async function(userId, active) {
    try {
        const res = await authFetch(`${API_BASE}/api/admin/users/status`, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ userId, active: String(active) })
        });
        const data = await res.json();
        if (data.success) {
            showToast("User status updated successfully.", "info");
            loadAdminUsers();
        } else {
            showToast("Failed: " + data.error, "error");
        }
    } catch (err) {
        showToast("Error: " + err.message, "error");
    }
};

window.openResetPwModal = function(userId, username) {
    if (elements.resetPwUserId) elements.resetPwUserId.value = userId;
    if (elements.resetPwUsername) elements.resetPwUsername.textContent = username;
    if (elements.resetPwInput) elements.resetPwInput.value = "";
    if (elements.adminResetPwModal) elements.adminResetPwModal.classList.remove("hidden");
};

window.closeResetPwModal = function() {
    if (elements.adminResetPwModal) elements.adminResetPwModal.classList.add("hidden");
};

// ============================================================================
// STUDENT FORGOT PASSWORD & OTP RECOVERY CONTROLLER
// ============================================================================

let fpState = {
    currentStep: 1,
    rollNumber: "",
    name: "",
    maskedEmail: "",
    maskedMobile: "",
    selectedChannel: "EMAIL",
    maskedDestination: "",
    demoOtp: "",
    otpTimerInterval: null,
    otpCooldownInterval: null,
    otpExpiresAt: 0,
    cooldownSeconds: 0
};

function clearFpTimers() {
    if (fpState.otpTimerInterval) {
        clearInterval(fpState.otpTimerInterval);
        fpState.otpTimerInterval = null;
    }
    if (fpState.otpCooldownInterval) {
        clearInterval(fpState.otpCooldownInterval);
        fpState.otpCooldownInterval = null;
    }
}

window.openForgotPasswordModal = function() {
    clearFpTimers();
    fpState = {
        currentStep: 1,
        rollNumber: "",
        name: "",
        maskedEmail: "",
        maskedMobile: "",
        selectedChannel: "EMAIL",
        maskedDestination: "",
        demoOtp: "",
        otpTimerInterval: null,
        otpCooldownInterval: null,
        otpExpiresAt: 0,
        cooldownSeconds: 0
    };

    // Reset all step inputs and error banners
    const step1Form = document.getElementById("fp-form-step1");
    if (step1Form) step1Form.reset();
    const step2Form = document.getElementById("fp-form-step2");
    if (step2Form) step2Form.reset();
    const step3Form = document.getElementById("fp-form-step3");
    if (step3Form) step3Form.reset();
    const step4Form = document.getElementById("fp-form-step4");
    if (step4Form) step4Form.reset();

    ["fp-step1-error", "fp-step2-error", "fp-step3-error", "fp-step4-error"].forEach(id => {
        const el = document.getElementById(id);
        if (el) { el.classList.add("hidden"); el.textContent = ""; }
    });

    const demoBanner = document.getElementById("fp-demo-otp-banner");
    if (demoBanner) demoBanner.classList.add("hidden");

    fpGoToStep(1);

    if (elements.forgotPasswordModal) {
        elements.forgotPasswordModal.classList.remove("hidden");
    }

    const rollInput = document.getElementById("fp-roll-input");
    if (rollInput) {
        if (elements.loginUsername && elements.loginUsername.value.trim()) {
            rollInput.value = elements.loginUsername.value.trim().toUpperCase();
        }
        setTimeout(() => rollInput.focus(), 100);
    }
};

window.closeForgotPasswordModal = function() {
    clearFpTimers();
    if (elements.forgotPasswordModal) {
        elements.forgotPasswordModal.classList.add("hidden");
    }
};

window.fpGoToStep = function(stepNum) {
    fpState.currentStep = stepNum;

    // Hide all step divs
    [1, 2, 3, 4, 5].forEach(s => {
        const stepDiv = document.getElementById(`fp-step-${s}`);
        if (stepDiv) {
            if (s === stepNum) {
                stepDiv.classList.remove("hidden");
            } else {
                stepDiv.classList.add("hidden");
            }
        }
    });

    const titleEl = document.getElementById("fp-modal-title");
    const indicatorEl = document.getElementById("fp-step-indicator");

    switch(stepNum) {
        case 1:
            if (titleEl) titleEl.textContent = "Student Password Recovery";
            if (indicatorEl) indicatorEl.textContent = "Step 1 of 4: Identify Student";
            break;
        case 2:
            if (titleEl) titleEl.textContent = "Verify Contact Channel";
            if (indicatorEl) indicatorEl.textContent = "Step 2 of 4: Select OTP Channel";
            break;
        case 3:
            if (titleEl) titleEl.textContent = "OTP Verification";
            if (indicatorEl) indicatorEl.textContent = "Step 3 of 4: Enter 6-Digit Code";
            break;
        case 4:
            if (titleEl) titleEl.textContent = "Create New Password";
            if (indicatorEl) indicatorEl.textContent = "Step 4 of 4: Set New Password";
            break;
        case 5:
            if (titleEl) titleEl.textContent = "Password Updated";
            if (indicatorEl) indicatorEl.textContent = "Account Secured";
            break;
    }
};

// Step 1: Identify Student by Roll Number
async function handleFpStep1Submit(e) {
    e.preventDefault();
    const rollInput = document.getElementById("fp-roll-input");
    const rollNumber = rollInput ? rollInput.value.trim().toUpperCase() : "";
    const errorEl = document.getElementById("fp-step1-error");
    const btn = document.getElementById("fp-btn-step1-next");
    const btnText = document.getElementById("fp-btn-step1-text");
    const btnSpinner = document.getElementById("fp-btn-step1-spinner");

    if (!rollNumber) {
        showFpError(errorEl, "Please enter your Roll Number.");
        return;
    }

    if (errorEl) errorEl.classList.add("hidden");
    if (btn) btn.disabled = true;
    if (btnText) btnText.textContent = "Verifying...";
    if (btnSpinner) btnSpinner.classList.remove("hidden");

    try {
        const res = await fetch(`${API_BASE}/api/auth/forgot-password/identify`, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ rollNumber })
        });
        const data = await res.json();

        if (res.ok && data.success) {
            fpState.rollNumber = data.rollNumber;
            fpState.name = data.name;
            fpState.maskedEmail = data.maskedEmail;
            fpState.maskedMobile = data.maskedMobile;

            // Populate Step 2 UI
            const nameEl = document.getElementById("fp-student-display-name");
            const rollEl = document.getElementById("fp-student-display-roll");
            const emailEl = document.getElementById("fp-masked-email");
            const mobileEl = document.getElementById("fp-masked-mobile");

            if (nameEl) nameEl.textContent = data.name;
            if (rollEl) rollEl.textContent = `Roll No: ${data.rollNumber}`;
            if (emailEl) emailEl.textContent = data.maskedEmail;
            if (mobileEl) mobileEl.textContent = data.maskedMobile;

            fpGoToStep(2);
        } else {
            showFpError(errorEl, data.error || "Student account not found. Please contact the Registrar/Administrator.");
        }
    } catch (err) {
        showFpError(errorEl, "Network error. Please check your connection and try again.");
    } finally {
        if (btn) btn.disabled = false;
        if (btnText) btnText.textContent = "Next: Identify Student";
        if (btnSpinner) btnSpinner.classList.add("hidden");
    }
}

// Step 2: Request OTP via Email or Mobile
async function handleFpStep2Submit(e) {
    e.preventDefault();
    const channelRadios = document.getElementsByName("fp-channel");
    let chosenChannel = "EMAIL";
    for (const r of channelRadios) {
        if (r.checked) {
            chosenChannel = r.value;
            break;
        }
    }
    fpState.selectedChannel = chosenChannel;

    const errorEl = document.getElementById("fp-step2-error");
    const btn = document.getElementById("fp-btn-step2-send");
    const btnText = document.getElementById("fp-btn-step2-text");
    const btnSpinner = document.getElementById("fp-btn-step2-spinner");

    if (errorEl) errorEl.classList.add("hidden");
    if (btn) btn.disabled = true;
    if (btnText) btnText.textContent = "Sending OTP...";
    if (btnSpinner) btnSpinner.classList.remove("hidden");

    try {
        const res = await fetch(`${API_BASE}/api/auth/forgot-password/send-otp`, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ rollNumber: fpState.rollNumber, channel: chosenChannel })
        });
        const data = await res.json();

        if (res.ok && data.success) {
            fpState.maskedDestination = data.maskedDestination;
            fpState.demoOtp = data.demoOtp;

            // Populate Step 3
            const destEl = document.getElementById("fp-sent-destination");
            if (destEl) destEl.textContent = `${data.channel === 'MOBILE' ? 'Mobile Number' : 'Email'} (${data.maskedDestination})`;

            const demoBanner = document.getElementById("fp-demo-otp-banner");
            const demoCode = document.getElementById("fp-demo-otp-code");
            if (data.demoOtp) {
                if (demoCode) demoCode.textContent = data.demoOtp;
                if (demoBanner) demoBanner.classList.remove("hidden");
            } else {
                if (demoBanner) demoBanner.classList.add("hidden");
            }

            // Start 5-minute expiry countdown (300 seconds)
            startFpOtpCountdown(5 * 60);

            // Start 45-second resend cooldown
            startFpResendCooldown(data.cooldownSeconds || 45);

            const otpInput = document.getElementById("fp-otp-input");
            if (otpInput) {
                otpInput.value = "";
                setTimeout(() => otpInput.focus(), 100);
            }

            fpGoToStep(3);
        } else {
            showFpError(errorEl, data.error || "Failed to dispatch OTP. Please try again.");
        }
    } catch (err) {
        showFpError(errorEl, "Network error while requesting OTP. Please try again.");
    } finally {
        if (btn) btn.disabled = false;
        if (btnText) btnText.textContent = "Send Verification OTP";
        if (btnSpinner) btnSpinner.classList.add("hidden");
    }
}

function startFpOtpCountdown(totalSeconds) {
    if (fpState.otpTimerInterval) clearInterval(fpState.otpTimerInterval);
    let remaining = totalSeconds;
    const timerEl = document.getElementById("fp-otp-timer");

    function update() {
        if (remaining <= 0) {
            if (timerEl) timerEl.textContent = "00:00 (Expired)";
            clearInterval(fpState.otpTimerInterval);
            fpState.otpTimerInterval = null;
            const errorEl = document.getElementById("fp-step3-error");
            showFpError(errorEl, "OTP expired. Please request a new OTP.");
            return;
        }
        const m = Math.floor(remaining / 60);
        const s = remaining % 60;
        if (timerEl) {
            timerEl.textContent = `${String(m).padStart(2, "0")}:${String(s).padStart(2, "0")}`;
        }
        remaining--;
    }
    update();
    fpState.otpTimerInterval = setInterval(update, 1000);
}

function startFpResendCooldown(cooldownSeconds) {
    if (fpState.otpCooldownInterval) clearInterval(fpState.otpCooldownInterval);
    let cd = cooldownSeconds;
    const resendBtn = document.getElementById("fp-btn-resend-otp");

    function updateCd() {
        if (cd <= 0) {
            if (resendBtn) {
                resendBtn.disabled = false;
                resendBtn.textContent = "Resend OTP";
                resendBtn.style.opacity = "1";
                resendBtn.style.cursor = "pointer";
            }
            clearInterval(fpState.otpCooldownInterval);
            fpState.otpCooldownInterval = null;
            return;
        }
        if (resendBtn) {
            resendBtn.disabled = true;
            resendBtn.textContent = `Resend in ${cd}s`;
            resendBtn.style.opacity = "0.6";
            resendBtn.style.cursor = "not-allowed";
        }
        cd--;
    }
    updateCd();
    fpState.otpCooldownInterval = setInterval(updateCd, 1000);
}

async function handleFpResendOtp() {
    const resendBtn = document.getElementById("fp-btn-resend-otp");
    if (resendBtn && resendBtn.disabled) return;

    const errorEl = document.getElementById("fp-step3-error");
    if (errorEl) errorEl.classList.add("hidden");

    try {
        const res = await fetch(`${API_BASE}/api/auth/forgot-password/send-otp`, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ rollNumber: fpState.rollNumber, channel: fpState.selectedChannel })
        });
        const data = await res.json();
        if (res.ok && data.success) {
            showToast("A new OTP has been dispatched.", "success");
            fpState.demoOtp = data.demoOtp;
            const demoCode = document.getElementById("fp-demo-otp-code");
            const demoBanner = document.getElementById("fp-demo-otp-banner");
            if (data.demoOtp) {
                if (demoCode) demoCode.textContent = data.demoOtp;
                if (demoBanner) demoBanner.classList.remove("hidden");
            }
            startFpOtpCountdown(5 * 60);
            startFpResendCooldown(data.cooldownSeconds || 45);
        } else {
            showFpError(errorEl, data.error || "Failed to resend OTP.");
        }
    } catch (e) {
        showFpError(errorEl, "Error resending OTP: " + e.message);
    }
}

// Step 3: Verify OTP Code
async function handleFpStep3Submit(e) {
    e.preventDefault();
    const otpInput = document.getElementById("fp-otp-input");
    const otp = otpInput ? otpInput.value.trim() : "";
    const errorEl = document.getElementById("fp-step3-error");
    const btn = document.getElementById("fp-btn-step3-verify");
    const btnText = document.getElementById("fp-btn-step3-text");
    const btnSpinner = document.getElementById("fp-btn-step3-spinner");

    if (!otp || otp.length !== 6) {
        showFpError(errorEl, "Please enter the valid 6-digit OTP.");
        return;
    }

    if (errorEl) errorEl.classList.add("hidden");
    if (btn) btn.disabled = true;
    if (btnText) btnText.textContent = "Verifying...";
    if (btnSpinner) btnSpinner.classList.remove("hidden");

    try {
        const res = await fetch(`${API_BASE}/api/auth/forgot-password/verify-otp`, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ rollNumber: fpState.rollNumber, otp })
        });
        const data = await res.json();

        if (res.ok && data.success) {
            clearFpTimers();
            const pwInput = document.getElementById("fp-new-password");
            const confInput = document.getElementById("fp-confirm-password");
            if (pwInput) pwInput.value = "";
            if (confInput) confInput.value = "";
            setTimeout(() => { if (pwInput) pwInput.focus(); }, 100);
            fpGoToStep(4);
        } else {
            showFpError(errorEl, data.error || "Invalid OTP. Please try again.");
        }
    } catch (err) {
        showFpError(errorEl, "Network error during OTP verification.");
    } finally {
        if (btn) btn.disabled = false;
        if (btnText) btnText.textContent = "Verify OTP";
        if (btnSpinner) btnSpinner.classList.add("hidden");
    }
}

// Step 4: Set New Password
async function handleFpStep4Submit(e) {
    e.preventDefault();
    const pwInput = document.getElementById("fp-new-password");
    const confInput = document.getElementById("fp-confirm-password");
    const newPassword = pwInput ? pwInput.value.trim() : "";
    const confirmPassword = confInput ? confInput.value.trim() : "";
    const errorEl = document.getElementById("fp-step4-error");
    const btn = document.getElementById("fp-btn-step4-reset");
    const btnText = document.getElementById("fp-btn-step4-text");
    const btnSpinner = document.getElementById("fp-btn-step4-spinner");

    if (newPassword.length < 8) {
        showFpError(errorEl, "Password must be at least 8 characters long.");
        return;
    }
    if (newPassword !== confirmPassword) {
        showFpError(errorEl, "Passwords do not match. Please verify both fields.");
        return;
    }

    if (errorEl) errorEl.classList.add("hidden");
    if (btn) btn.disabled = true;
    if (btnText) btnText.textContent = "Updating...";
    if (btnSpinner) btnSpinner.classList.remove("hidden");

    try {
        const res = await fetch(`${API_BASE}/api/auth/forgot-password/reset-password`, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({
                rollNumber: fpState.rollNumber,
                newPassword,
                confirmPassword
            })
        });
        const data = await res.json();

        if (res.ok && data.success) {
            fpGoToStep(5);
        } else {
            showFpError(errorEl, data.error || "Failed to update password. Please try again.");
        }
    } catch (err) {
        showFpError(errorEl, "Network error while saving new password.");
    } finally {
        if (btn) btn.disabled = false;
        if (btnText) btnText.textContent = "Update Password";
        if (btnSpinner) btnSpinner.classList.add("hidden");
    }
}

function showFpError(el, msg) {
    if (!el) return;
    el.innerHTML = `<span>⚠️</span> <span>${escapeHtml(msg)}</span>`;
    el.classList.remove("hidden");
}

window.fpFinishAndGoToLogin = function() {
    const savedRoll = fpState.rollNumber;
    closeForgotPasswordModal();
    showLoginView();

    // Select Student role tab
    const studentPill = document.getElementById("role-btn-student");
    if (studentPill) studentPill.click();

    // Fill in Roll Number and clear password
    if (elements.loginUsername) {
        elements.loginUsername.value = savedRoll;
    }
    if (elements.loginPassword) {
        elements.loginPassword.value = "";
        elements.loginPassword.focus();
    }
    showToast("Password updated! Please sign in with your new password.", "success");
};

// ============================================================================
// REGISTRAR & ADVISOR STUDENT ACCOUNT DETAILS LOOKUP
// ============================================================================

async function handleStudentLookupSearch(e) {
    if (e) e.preventDefault();
    const input = document.getElementById("registrar-search-student-input");
    const resultBox = document.getElementById("registrar-student-lookup-result");
    const clearBtn = document.getElementById("btn-registrar-clear-student");
    const searchBtn = document.getElementById("btn-registrar-search-student");

    if (!input || !resultBox) return;
    const query = input.value.trim();

    if (!query) {
        showToast("Please enter a Roll Number or Student Name to search.", "warning");
        return;
    }

    if (searchBtn) searchBtn.disabled = true;

    try {
        const res = await authFetch(`${API_BASE}/api/registrar/student-lookup?query=${encodeURIComponent(query)}`);
        const data = await res.json();

        if (clearBtn) clearBtn.style.display = "inline-block";
        resultBox.classList.remove("hidden");

        if (!res.ok || !data.success || !data.student) {
            resultBox.innerHTML = `
                <div style="padding: 1.1rem; border-radius: var(--radius-sm); background: rgba(239, 68, 68, 0.1); border: 1px solid rgba(239, 68, 68, 0.3); color: #fca5a5; display: flex; align-items: center; gap: 0.75rem;">
                    <span style="font-size: 1.4rem;">⚠️</span>
                    <div>
                        <strong style="color: #f87171; display: block;">No student account found.</strong>
                        <span style="font-size: 0.86rem; color: #fca5a5;">No student data found matching "<strong>${escapeHtml(query)}</strong>". Please verify the Roll Number or Name.</span>
                    </div>
                </div>
            `;
            return;
        }

        const s = data.student;

        // Risk badge styling
        let riskBadgeClass = "badge-success";
        let riskLabel = "✅ Low Risk (Good Standing)";
        if (s.riskFlag === "HIGH") {
            riskBadgeClass = "badge-danger";
            riskLabel = `🚨 High Risk (${s.riskScore ? Number(s.riskScore).toFixed(3) : '0.700'})`;
        } else if (s.riskFlag === "MEDIUM") {
            riskBadgeClass = "badge-warning";
            riskLabel = `⚠️ Medium Risk (${s.riskScore ? Number(s.riskScore).toFixed(3) : '0.350'})`;
        } else {
            riskLabel = `✅ Low Risk (${s.riskScore ? Number(s.riskScore).toFixed(3) : '0.050'})`;
        }

        // Account status badge styling
        const statusBadge = (s.accountStatus === "Active")
            ? `<span class="badge badge-success">ACTIVE ACCOUNT</span>`
            : (s.accountStatus === "Inactive")
                ? `<span class="badge badge-danger">INACTIVE ACCOUNT</span>`
                : `<span class="badge badge-warning">NOT PROVISIONED</span>`;

        resultBox.innerHTML = `
            <div style="background: var(--bg-surface-elevated); border: 1px solid var(--border-color); border-radius: var(--radius-md); padding: 1.25rem;">
                <!-- Header row -->
                <div style="display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 1.2rem; flex-wrap: wrap; gap: 0.75rem;">
                    <div>
                        <div style="display: flex; align-items: center; gap: 0.6rem; flex-wrap: wrap;">
                            <h3 style="margin: 0; font-size: 1.25rem; color: #ffffff;">${escapeHtml(s.name)}</h3>
                            <span class="badge badge-info" style="font-family: monospace; font-size: 0.85rem;">${escapeHtml(s.rollNumber)}</span>
                        </div>
                        <span style="font-size: 0.82rem; color: var(--text-muted);">Student ID: #${s.studentId}</span>
                    </div>
                    <div style="display: flex; gap: 0.5rem; align-items: center; flex-wrap: wrap;">
                        ${statusBadge}
                        <span class="badge ${riskBadgeClass}">${riskLabel}</span>
                    </div>
                </div>

                <!-- 2-Column Details Grid -->
                <div style="display: grid; grid-template-columns: repeat(auto-fit, minmax(240px, 1fr)); gap: 1rem; margin-bottom: 1.2rem; background: var(--bg-surface); padding: 1rem; border-radius: var(--radius-sm); border: 1px solid rgba(255, 255, 255, 0.05);">
                    <div>
                        <span style="font-size: 0.75rem; color: var(--text-muted); text-transform: uppercase; font-weight: 600; display: block; margin-bottom: 0.2rem;">Registered Email</span>
                        <strong style="font-size: 0.92rem; color: #93c5fd; font-family: monospace;">${escapeHtml(s.email || 'None')}</strong>
                    </div>
                    <div>
                        <span style="font-size: 0.75rem; color: var(--text-muted); text-transform: uppercase; font-weight: 600; display: block; margin-bottom: 0.2rem;">Registered Mobile</span>
                        <strong style="font-size: 0.92rem; color: var(--text-primary); font-family: monospace;">${escapeHtml(s.mobile || 'Not Registered')}</strong>
                    </div>
                    <div>
                        <span style="font-size: 0.75rem; color: var(--text-muted); text-transform: uppercase; font-weight: 600; display: block; margin-bottom: 0.2rem;">Academic Standing</span>
                        <span style="font-size: 0.92rem; color: var(--text-primary);">
                            GPA: <strong>${s.gpa ? Number(s.gpa).toFixed(2) : '0.00'}</strong> / 4.00 &bull; Attendance: <strong>${s.attendancePct ? Number(s.attendancePct).toFixed(1) : '0.0'}%</strong>
                        </span>
                    </div>
                    <div>
                        <span style="font-size: 0.75rem; color: var(--text-muted); text-transform: uppercase; font-weight: 600; display: block; margin-bottom: 0.2rem;">Enrolled Courses</span>
                        <span style="font-size: 0.92rem; color: var(--text-primary);">
                            <strong style="color: #34d399;">${s.enrolledCoursesCount || 0}</strong> Active Registered Courses
                        </span>
                    </div>
                </div>

                <!-- Actions row -->
                <div style="display: flex; justify-content: flex-end; gap: 0.6rem;">
                    <button type="button" class="btn btn-secondary btn-sm" onclick="selectAndInspectStudent(${s.studentId})">
                        <span>📋 View in Student Records</span>
                    </button>
                </div>
            </div>
        `;
    } catch (err) {
        resultBox.innerHTML = `
            <div style="padding: 1rem; border-radius: var(--radius-sm); background: rgba(239, 68, 68, 0.1); border: 1px solid rgba(239, 68, 68, 0.3); color: #fca5a5;">
                Lookup error: ${escapeHtml(err.message)}
            </div>
        `;
    } finally {
        if (searchBtn) searchBtn.disabled = false;
    }
}

window.selectAndInspectStudent = function(studentId) {
    if (elements.studentSelector) {
        elements.studentSelector.value = studentId;
        state.currentStudentId = studentId;
        loadStudentData(studentId);
    }
    switchTab("student-tab");
    showToast("Loaded student records.", "info");
};

window.clearStudentLookup = function() {
    const input = document.getElementById("registrar-search-student-input");
    const resultBox = document.getElementById("registrar-student-lookup-result");
    const clearBtn = document.getElementById("btn-registrar-clear-student");
    if (input) input.value = "";
    if (resultBox) {
        resultBox.classList.add("hidden");
        resultBox.innerHTML = "";
    }
    if (clearBtn) clearBtn.style.display = "none";
};

window.submitResetPassword = async function() {
    const userId = elements.resetPwUserId.value;
    const newPassword = elements.resetPwInput.value.trim();
    if (!newPassword) {
        showToast("Password cannot be empty.", "warning");
        return;
    }
    try {
        const res = await authFetch(`${API_BASE}/api/admin/users/reset-password`, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ userId, newPassword })
        });
        const data = await res.json();
        if (data.success) {
            showToast("Password reset successfully.", "success");
            closeResetPwModal();
        } else {
            showToast("Failed: " + data.error, "error");
        }
    } catch (err) {
        showToast("Error: " + err.message, "error");
    }
};

window.deleteUser = async function(userId, username) {
    if (!confirm(`Are you sure you want to delete user account '${username}'?`)) return;
    try {
        const res = await authFetch(`${API_BASE}/api/admin/users/delete`, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ userId })
        });
        const data = await res.json();
        if (data.success) {
            showToast(`User '${username}' deleted.`, "info");
            loadAdminUsers();
            loadAdminOverview();
        } else {
            showToast("Failed: " + data.error, "error");
        }
    } catch (err) {
        showToast("Error: " + err.message, "error");
    }
};

async function loadAdminStudents() {
    try {
        const res = await authFetch(`${API_BASE}/api/students`);
        if (!res.ok) return;
        const students = await res.json();
        if (!elements.adminStudentsTbody) return;

        // Sync Admin Student Risk filter button group
        document.querySelectorAll("#admin-student-risk-filter-group .btn-filter-risk").forEach(btn => {
            if (btn.dataset.risk === state.adminRiskFilter) {
                btn.classList.add("active");
            } else {
                btn.classList.remove("active");
            }
        });

        // Sync Admin Overview KPI card active states
        document.querySelectorAll(".admin-kpi-grid .kpi-filter-card").forEach(card => {
            if (card.dataset.risk === state.adminRiskFilter) {
                card.classList.add("active-filter");
            } else {
                card.classList.remove("active-filter");
            }
        });

        // Update status text & reset button
        if (elements.adminStudentFilterStatus) {
            const label = state.adminRiskFilter === "ALL" 
                ? `All Students (${students.length})` 
                : `${state.adminRiskFilter} Risk Cohort`;
            elements.adminStudentFilterStatus.innerHTML = `Showing: <strong style="color: var(--primary);">${label}</strong>`;
        }
        if (elements.btnResetAdminFilter) {
            elements.btnResetAdminFilter.style.display = state.adminRiskFilter === "ALL" ? "none" : "inline-block";
        }

        // Apply filter
        let filtered = students;
        if (state.adminRiskFilter === "HIGH") {
            filtered = students.filter(s => s.riskFlag === "HIGH");
        } else if (state.adminRiskFilter === "MEDIUM") {
            filtered = students.filter(s => s.riskFlag === "MEDIUM");
        } else if (state.adminRiskFilter === "LOW") {
            filtered = students.filter(s => s.riskFlag === "LOW");
        }

        if (filtered.length === 0) {
            elements.adminStudentsTbody.innerHTML = `<tr><td colspan="8" style="text-align: center; color: var(--text-muted); padding: 1.5rem;">No students found matching <strong>${state.adminRiskFilter} Risk</strong> criteria.</td></tr>`;
            return;
        }

        elements.adminStudentsTbody.innerHTML = filtered.map(s => {
            const riskBadge = s.riskFlag === "HIGH" ? `<span class="badge badge-danger">HIGH RISK</span>`
                : (s.riskFlag === "MEDIUM" ? `<span class="badge badge-warning">MEDIUM</span>` : `<span class="badge badge-success">LOW</span>`);

            return `
                <tr>
                    <td style="font-family: monospace;">#${s.studentId}</td>
                    <td style="font-family: monospace; font-weight: 600;">${escapeHtml(s.rollNumber)}</td>
                    <td><strong>${escapeHtml(s.name)}</strong></td>
                    <td style="font-size: 0.85rem; color: var(--text-secondary);">${escapeHtml(s.email)}</td>
                    <td style="font-weight: 600; color: ${s.gpa < 2.5 ? 'var(--danger)' : 'var(--text-primary)'};">${s.gpa.toFixed(2)}</td>
                    <td>${s.attendancePct.toFixed(1)}%</td>
                    <td>${riskBadge}</td>
                    <td>
                        <button class="btn btn-danger btn-sm" onclick="deleteStudent(${s.studentId}, '${escapeHtml(s.name)}')">Delete</button>
                    </td>
                </tr>
            `;
        }).join("");
    } catch (err) {
        console.error("Failed to load admin students:", err);
    }
}

window.deleteStudent = async function(studentId, name) {
    if (!confirm(`Are you sure you want to permanently delete student '${name}' and all associated enrollments?`)) return;
    try {
        const res = await authFetch(`${API_BASE}/api/admin/student/delete`, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ studentId })
        });
        const data = await res.json();
        if (data.success) {
            showToast(`Student '${name}' deleted successfully.`, "info");
            await loadInitialData();
            loadAdminStudents();
            loadAdminOverview();
        } else {
            showToast("Failed to delete student: " + data.error, "error");
        }
    } catch (err) {
        showToast("Error: " + err.message, "error");
    }
};

async function loadAdminCourses() {
    try {
        const [cRes, dRes] = await Promise.all([
            authFetch(`${API_BASE}/api/courses`),
            authFetch(`${API_BASE}/api/dag`)
        ]);
        if (!cRes.ok || !dRes.ok) return;
        const courses = await cRes.json();
        const dag = await dRes.json();

        if (elements.adminCoursesTbody) {
            elements.adminCoursesTbody.innerHTML = courses.map(c => `
                <tr>
                    <td style="font-family: monospace; font-weight: 600; color: var(--primary);">${escapeHtml(c.courseCode)}</td>
                    <td><strong>${escapeHtml(c.courseName)}</strong></td>
                    <td>${c.credits} cr</td>
                    <td>${escapeHtml(c.department)}</td>
                    <td>
                        <button class="btn btn-danger btn-sm" onclick="deleteCourse(${c.courseId}, '${escapeHtml(c.courseCode)}')">Delete</button>
                    </td>
                </tr>
            `).join("");
        }

        if (elements.adminPrereqsTbody) {
            const pairs = dag.prerequisites || [];
            elements.adminPrereqsTbody.innerHTML = pairs.length === 0
                ? `<tr><td colspan="3" class="text-muted" style="text-align: center; padding: 1rem;">No prerequisite constraints registered.</td></tr>`
                : pairs.map(p => `
                    <tr>
                        <td style="font-family: monospace; font-weight: 600; color: #93c5fd;">${escapeHtml(p.targetCourseCode)} - ${escapeHtml(p.targetCourseName)}</td>
                        <td style="font-family: monospace; font-weight: 600; color: #fde047;">${escapeHtml(p.prerequisiteCode)} - ${escapeHtml(p.prerequisiteName)}</td>
                        <td>
                            <button class="btn btn-danger btn-sm" onclick="deletePrerequisite(${p.courseId}, ${p.prerequisiteId}, '${escapeHtml(p.targetCourseCode)} -> ${escapeHtml(p.prerequisiteCode)}')">Remove Link</button>
                        </td>
                    </tr>
                `).join("");
        }
    } catch (err) {
        console.error("Failed to load admin courses/prereqs:", err);
    }
}

window.deleteCourse = async function(courseId, code) {
    if (!confirm(`Are you sure you want to delete course ${code}? This will also delete timetable slots and enrollments for this course.`)) return;
    try {
        const res = await authFetch(`${API_BASE}/api/admin/course/delete`, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ courseId })
        });
        const data = await res.json();
        if (data.success) {
            showToast(`Course ${code} deleted successfully.`, "info");
            await loadInitialData();
            loadAdminCourses();
            loadAdminOverview();
        } else {
            showToast("Failed to delete course: " + data.error, "error");
        }
    } catch (err) {
        showToast("Error: " + err.message, "error");
    }
};

window.deletePrerequisite = async function(courseId, prerequisiteId, label) {
    if (!confirm(`Are you sure you want to delete prerequisite link ${label}?`)) return;
    try {
        const res = await authFetch(`${API_BASE}/api/admin/prerequisite/delete`, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ courseId, prerequisiteId })
        });
        const data = await res.json();
        if (data.success) {
            showToast("Prerequisite link deleted successfully.", "info");
            await loadInitialData();
            loadAdminCourses();
        } else {
            showToast("Failed to delete prerequisite: " + data.error, "error");
        }
    } catch (err) {
        showToast("Error: " + err.message, "error");
    }
};

async function loadAdminTimetable() {
    try {
        const res = await authFetch(`${API_BASE}/api/courses`);
        if (!res.ok) return;
        const courses = await res.json();
        if (!elements.adminSlotsTbody) return;

        let allSlots = [];
        courses.forEach(c => {
            if (c.slots) {
                c.slots.forEach(sl => {
                    allSlots.push({ ...sl, courseCode: c.courseCode, courseName: c.courseName });
                });
            }
        });

        elements.adminSlotsTbody.innerHTML = allSlots.length === 0
            ? `<tr><td colspan="7" class="text-muted" style="text-align: center; padding: 1rem;">No timetable slots registered.</td></tr>`
            : allSlots.map(sl => `
                <tr>
                    <td style="font-family: monospace;">#${sl.slotId}</td>
                    <td style="font-weight: 600; color: var(--primary);">${escapeHtml(sl.courseCode)}</td>
                    <td><span class="badge badge-info">${escapeHtml(sl.dayOfWeek)}</span></td>
                    <td style="font-family: monospace;">${escapeHtml(sl.startTime)} - ${escapeHtml(sl.endTime)}</td>
                    <td><strong>${escapeHtml(sl.roomNumber)}</strong></td>
                    <td>${escapeHtml(sl.facultyName || 'Faculty')}</td>
                    <td>
                        <button class="btn btn-danger btn-sm" onclick="deleteSlot(${sl.slotId}, '${escapeHtml(sl.courseCode)}')">Delete</button>
                    </td>
                </tr>
            `).join("");
    } catch (err) {
        console.error("Failed to load admin timetable slots:", err);
    }
}

window.deleteSlot = async function(slotId, courseCode) {
    if (!confirm(`Are you sure you want to delete slot #${slotId} for course ${courseCode}?`)) return;
    try {
        const res = await authFetch(`${API_BASE}/api/admin/slot/delete`, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ slotId })
        });
        const data = await res.json();
        if (data.success) {
            showToast("Timetable slot deleted successfully.", "info");
            await loadInitialData();
            loadAdminTimetable();
        } else {
            showToast("Failed to delete slot: " + data.error, "error");
        }
    } catch (err) {
        showToast("Error: " + err.message, "error");
    }
};

window.loadAdminReports = async function() {
    try {
        const res = await authFetch(`${API_BASE}/api/admin/reports`);
        if (!res.ok) return;
        const data = await res.json();

        // 1. Risk breakdown
        if (elements.adminReportRisk && data.riskBreakdown) {
            const rb = data.riskBreakdown;
            elements.adminReportRisk.innerHTML = `
                <div style="display: grid; grid-template-columns: repeat(3, 1fr); gap: 0.75rem; margin-bottom: 1rem;">
                    <div class="kpi-filter-card" onclick="filterAdminStudentsByRisk('HIGH')" style="background: rgba(239, 68, 68, 0.12); border: 1px solid var(--danger); border-radius: var(--radius-sm); padding: 0.75rem; text-align: center; cursor: pointer;" title="Click to view High Risk students in Student Records">
                        <span style="font-size: 1.5rem; font-weight: 700; color: #f87171; display: block;">${rb.highCount}</span>
                        <span style="font-size: 0.75rem; color: #fca5a5; font-weight: 600;">🚨 High Risk</span>
                        <span style="font-size: 0.68rem; color: #f87171; display: block; margin-top: 2px;">Inspect &rarr;</span>
                    </div>
                    <div class="kpi-filter-card" onclick="filterAdminStudentsByRisk('MEDIUM')" style="background: rgba(245, 158, 11, 0.12); border: 1px solid var(--warning); border-radius: var(--radius-sm); padding: 0.75rem; text-align: center; cursor: pointer;" title="Click to view Medium Risk students in Student Records">
                        <span style="font-size: 1.5rem; font-weight: 700; color: #fbbf24; display: block;">${rb.mediumCount}</span>
                        <span style="font-size: 0.75rem; color: #fde68a; font-weight: 600;">⚠️ Medium Risk</span>
                        <span style="font-size: 0.68rem; color: #fbbf24; display: block; margin-top: 2px;">Inspect &rarr;</span>
                    </div>
                    <div class="kpi-filter-card" onclick="filterAdminStudentsByRisk('LOW')" style="background: rgba(16, 185, 129, 0.12); border: 1px solid var(--success); border-radius: var(--radius-sm); padding: 0.75rem; text-align: center; cursor: pointer;" title="Click to view Low Risk students in Student Records">
                        <span style="font-size: 1.5rem; font-weight: 700; color: #34d399; display: block;">${rb.lowCount}</span>
                        <span style="font-size: 0.75rem; color: #a7f3d0; font-weight: 600;">✅ Good Standing</span>
                        <span style="font-size: 0.68rem; color: #34d399; display: block; margin-top: 2px;">Inspect &rarr;</span>
                    </div>
                </div>
                <div style="font-size: 0.85rem; color: var(--text-secondary); line-height: 1.6;">
                    Cohort Average GPA: <strong>${rb.avgGpa.toFixed(2)}</strong> / 4.0<br>
                    Cohort Average Attendance: <strong>${rb.avgAttendance.toFixed(1)}%</strong>
                </div>
            `;
        }

        // 2. Dept Breakdown
        if (elements.adminReportDept && data.departmentBreakdown) {
            elements.adminReportDept.innerHTML = data.departmentBreakdown.map(d => `
                <div style="display: flex; justify-content: space-between; align-items: center; padding: 0.6rem 0; border-bottom: 1px solid var(--border-color);">
                    <span style="font-weight: 600; color: var(--text-primary);">${escapeHtml(d.department)}</span>
                    <span class="badge badge-info">${d.totalCourses} Courses</span>
                </div>
            `).join("");
        }

        // 3. Course Enrollment Density
        if (elements.adminReportCoursesTbody && data.courseEnrollments) {
            elements.adminReportCoursesTbody.innerHTML = data.courseEnrollments.map(c => `
                <tr>
                    <td style="font-family: monospace; font-weight: 600; color: var(--primary);">${escapeHtml(c.courseCode)}</td>
                    <td><strong>${escapeHtml(c.courseName)}</strong></td>
                    <td>${escapeHtml(c.department)}</td>
                    <td>${c.credits} cr</td>
                    <td>
                        <div style="display: flex; align-items: center; gap: 0.5rem;">
                            <strong style="color: ${c.enrolledCount > 0 ? '#34d399' : 'var(--text-muted)'};">${c.enrolledCount}</strong>
                            <div style="flex: 1; height: 6px; background: var(--bg-main); border-radius: 3px; max-width: 100px; overflow: hidden;">
                                <div style="height: 100%; width: ${Math.min(100, c.enrolledCount * 20)}%; background: #3b82f6; border-radius: 3px;"></div>
                            </div>
                        </div>
                    </td>
                </tr>
            `).join("");
        }
    } catch (err) {
        console.error("Failed to load admin reports:", err);
    }
};
