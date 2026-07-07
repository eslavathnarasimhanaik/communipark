const API_BASE_URL = window.location.hostname === 'localhost' ? 'http://localhost:8080/api' : `${window.location.origin}/api`;
let userTypeChart = null;
let vehicleChart = null;
let pollingInterval = null;
let isPerformingAction = false;

document.addEventListener("DOMContentLoaded", () => {
    checkAdminSession();
    startPolling();
    setupEventDelegation();
});

// Setup Event Delegation for dynamic tables (more robust than inline onclick)
function setupEventDelegation() {
    const reservationsBody = document.getElementById("admin-reservations-body");
    if (reservationsBody) {
        reservationsBody.addEventListener('click', (event) => {
            const btn = event.target.closest('button');
            const link = event.target.closest('.print-link');
            
            if (btn) {
                const action = btn.getAttribute('data-action');
                const resId = parseInt(btn.getAttribute('data-res-id') || '0');
                const spotId = parseInt(btn.getAttribute('data-spot-id') || '0');
                
                if (action === 'release') {
                    forceReleaseSpot(resId, spotId);
                } else if (action === 'confirm-pay') {
                    adminConfirmPayment(resId);
                } else if (action === 'delete') {
                    purgeReservationRecord(resId);
                } else if (action === 'print') {
                    printReceiptForReservation(resId);
                }
            } else if (link) {
                event.preventDefault();
                const resId = parseInt(link.getAttribute('data-res-id') || '0');
                printReceiptForReservation(resId);
            }
        });
    }

    const spotsBody = document.getElementById("admin-spots-body");
    if (spotsBody) {
        spotsBody.addEventListener('click', (event) => {
            const btn = event.target.closest('button');
            if (btn) {
                const action = btn.getAttribute('data-action');
                const resId = parseInt(btn.getAttribute('data-res-id') || '0');
                const spotId = parseInt(btn.getAttribute('data-spot-id') || '0');
                
                if (action === 'release') {
                    forceReleaseSpot(resId, spotId);
                }
            }
        });
    }
}

// Start real-time polling every 3 seconds
function startPolling() {
    if (pollingInterval) clearInterval(pollingInterval);
    pollingInterval = setInterval(async () => {
        const isLoggedIn = sessionStorage.getItem('adminSession') === 'true';
        if (isLoggedIn && !isPerformingAction) {
            await fetchAdminData();
        }
    }, 3000);
}

// Stop polling
function stopPolling() {
    if (pollingInterval) {
        clearInterval(pollingInterval);
        pollingInterval = null;
    }
}

// Check if admin is logged in
function checkAdminSession() {
    const isLoggedIn = sessionStorage.getItem('adminSession') === 'true';
    const loginContainer = document.getElementById('admin-login-container');
    const panelContainer = document.getElementById('admin-panel-container');

    if (isLoggedIn) {
        if (loginContainer) loginContainer.style.display = 'none';
        if (panelContainer) panelContainer.style.display = 'block';
        fetchAdminData();
    } else {
        if (loginContainer) loginContainer.style.display = 'flex';
        if (panelContainer) panelContainer.style.display = 'none';
    }
}

// Handle login submit
function handleAdminLogin(event) {
    event.preventDefault();
    const u = document.getElementById('adminUsername').value;
    const p = document.getElementById('adminPassword').value;
    const errorEl = document.getElementById('login-error-msg');

    if (u === 'admin' && p === 'admin123') {
        sessionStorage.setItem('adminSession', 'true');
        if (errorEl) errorEl.style.display = 'none';
        checkAdminSession();
        startPolling();
    } else {
        if (errorEl) {
            errorEl.textContent = '❌ Access Denied: Invalid security credentials.';
            errorEl.style.display = 'block';
        }
    }
}

// Handle logout
function adminLogout() {
    sessionStorage.removeItem('adminSession');
    stopPolling();
    alert('Logged out successfully.');
    checkAdminSession();
}

async function fetchAdminData() {
    try {
        const statsResponse = await fetch(`${API_BASE_URL}/dashboard/stats`);
        if (!statsResponse.ok) throw new Error("Failed to load analytics");
        const stats = await statsResponse.json();

        // 1. Update Aggregated Statistics Cards
        const totalBookingsEl = document.getElementById("admin-total-bookings");
        const sumRevenueEl = document.getElementById("admin-sum-revenue");
        const sumHoursEl = document.getElementById("admin-sum-hours");
        const occupancyEl = document.getElementById("admin-occupancy");

        if (totalBookingsEl) totalBookingsEl.textContent = stats.totalBookings || '0';
        if (sumRevenueEl) sumRevenueEl.textContent = stats.totalRevenue || '₹0';
        if (sumHoursEl) sumHoursEl.textContent = stats.totalHours || '0h';
        if (occupancyEl) occupancyEl.textContent = stats.occupancyRate || '0%';

        // 2. Render Charts
        updateCharts(stats);

        // 3. Render Parking Spots grid
        renderSpotsGrid(stats.parkingDetails);

        // 4. Fetch and render all reservation logs
        await fetchAndRenderAllLogs();

        // Remove any connection error alert if it exists
        const dbAlert = document.getElementById('db-admin-alert');
        if (dbAlert) dbAlert.remove();

    } catch (error) {
        console.error("Admin data fetch error:", error);
        showDbAlert();
    }
}

function updateCharts(stats) {
    const userTypeCtxEl = document.getElementById("userTypeChart");
    if (userTypeCtxEl) {
        const userTypeCtx = userTypeCtxEl.getContext("2d");
        if (userTypeChart) userTypeChart.destroy();
        userTypeChart = new Chart(userTypeCtx, {
            type: "doughnut",
            data: {
                labels: ["Residents", "Visitors"],
                datasets: [{
                    data: stats.residentVsVisitor || [3, 2],
                    backgroundColor: ["#3b82f6", "#f59e0b"],
                    borderWidth: 0
                }]
            },
            options: {
                responsive: true,
                maintainAspectRatio: false,
                plugins: {
                    legend: { position: 'bottom', labels: { boxWidth: 12, padding: 15, color: '#9ca3af' } }
                }
            }
        });
    }

    const vehicleCtxEl = document.getElementById("adminVehicleChart");
    if (vehicleCtxEl) {
        const vehicleCtx = vehicleCtxEl.getContext("2d");
        if (vehicleChart) vehicleChart.destroy();
        vehicleChart = new Chart(vehicleCtx, {
            type: "doughnut",
            data: {
                labels: ["Cars", "Bikes", "Trucks"],
                datasets: [{
                    data: stats.vehicleTypeDistribution || [2, 1, 1],
                    backgroundColor: ["#818cf8", "#10b981", "#ec4899"],
                    borderWidth: 0
                }]
            },
            options: {
                responsive: true,
                maintainAspectRatio: false,
                plugins: {
                    legend: { position: 'bottom', labels: { boxWidth: 12, padding: 15, color: '#9ca3af' } }
                }
            }
        });
    }
}

function getIndianCategoryName(spot) {
    if (spot.type && spot.type.toLowerCase() === 'vip') return "VIP Owner";
    if (spot.id === 3 || spot.id === 6) return "2W (Two-Wheeler)";
    return "4W (Four-Wheeler)";
}

function renderSpotsGrid(spots) {
    const tbody = document.getElementById("admin-spots-body");
    if (!tbody || !spots) return;
    tbody.innerHTML = "";

    spots.forEach(s => {
        const row = document.createElement("tr");
        const isOccupied = s.status && s.status.toLowerCase() === "occupied";
        const statusColor = isOccupied ? "color: #f43f5e; font-weight:600;" : "color: #06b6d4; font-weight:600;";
        const categoryLabel = getIndianCategoryName(s);

        let actionBtn = "-";
        if (isOccupied && s.reservationId) {
            actionBtn = `
                <button class="release-btn" data-action="release" data-res-id="${s.reservationId}" data-spot-id="${s.id}" style="padding: 4px 8px; font-size: 0.8rem;">
                    <i class="fas fa-sign-out-alt"></i> Force Release
                </button>
            `;
        }

        row.innerHTML = `
            <td><strong>Slot #${s.id}</strong></td>
            <td><span class="spot-type-badge">${categoryLabel}</span></td>
            <td><span style="${statusColor}">${(s.status || 'unknown').toUpperCase()}</span></td>
            <td><code>${s.vehicle !== 'N/A' ? s.vehicle : '-'}</code></td>
            <td><span style="color: #a5b4fc; font-weight:500;">${s.apartmentNumber !== 'N/A' ? 'Unit ' + s.apartmentNumber : '-'}</span></td>
            <td>${s.userType !== 'N/A' ? s.userType : '-'}</td>
            <td>${s.duration}</td>
            <td><strong>${s.revenue}</strong></td>
            <td>${actionBtn}</td>
        `;
        tbody.appendChild(row);
    });
}

async function fetchAndRenderAllLogs() {
    const tbody = document.getElementById("admin-reservations-body");
    if (!tbody) return;

    try {
        const response = await fetch(`${API_BASE_URL}/reservations`);
        if (!response.ok) throw new Error("Failed to fetch reservation list");
        const reservations = await response.json();

        reservations.sort((a, b) => b.id - a.id);
        tbody.innerHTML = "";

        if (reservations.length === 0) {
            tbody.innerHTML = `<tr><td colspan="13" style="text-align: center; color: #6b7280; padding: 2rem;">No reservation logs in database.</td></tr>`;
            return;
        }

        reservations.forEach(r => {
            const row = document.createElement("tr");
            const isCompleted = r.status && r.status.toLowerCase() === 'completed';
            const isPaid = r.paymentStatus === 'PAID';

            const statusStyle = isCompleted
                ? 'background: rgba(16, 185, 129, 0.1); color: #10b981; padding: 4px 8px; border-radius: 4px; font-weight: 600;'
                : 'background: rgba(59, 130, 246, 0.1); color: #3b82f6; padding: 4px 8px; border-radius: 4px; font-weight: 600;';

            let actionButtons = "";

            // Release button — only for active reservations
            if (!isCompleted) {
                actionButtons += `
                    <button class="release-btn" data-action="release" data-res-id="${r.id}" data-spot-id="${r.parkingSpot ? r.parkingSpot.id : 0}" style="padding: 4px 8px; font-size: 0.8rem; margin-right: 0.25rem;">
                        <i class="fas fa-sign-out-alt"></i> Release
                    </button>
                `;
            }

            // Confirm Pay — only for non-paid reservations that have a tracked payment method
            if (!isPaid && r.paymentMethod) {
                actionButtons += `
                    <button data-action="confirm-pay" data-res-id="${r.id}" style="background: rgba(16, 185, 129, 0.15); color: #10b981; border: 1px solid rgba(16, 185, 129, 0.3); padding: 4px 8px; font-size: 0.8rem; border-radius: 6px; cursor: pointer; transition: 0.2s; margin-right: 0.25rem;">
                        <i class="fas fa-check"></i> Confirm Pay
                    </button>
                `;
            }

            // Print Receipt — for paid reservations
            if (isPaid) {
                actionButtons += `
                    <button data-action="print" data-res-id="${r.id}" style="background: rgba(129, 140, 248, 0.15); color: #818cf8; border: 1px solid rgba(129,140,248,0.3); padding: 4px 8px; font-size: 0.8rem; border-radius: 6px; cursor: pointer; transition: 0.2s; margin-right: 0.25rem;">
                        <i class="fas fa-print"></i> Print Receipt
                    </button>
                `;
            }

            // Delete Log — always available
            actionButtons += `
                <button data-action="delete" data-res-id="${r.id}" style="background: rgba(239, 68, 68, 0.15); color: #ef4444; border: 1px solid rgba(239,68,68,0.3); padding: 4px 8px; font-size: 0.8rem; border-radius: 6px; cursor: pointer; transition: 0.2s;">
                    <i class="fas fa-trash"></i> Delete Log
                </button>
            `;

            let spotCategory = '4W';
            if (r.parkingSpot) {
                if (r.parkingSpot.type === 'vip') spotCategory = 'VIP Owner';
                else if (r.parkingSpot.id === 3 || r.parkingSpot.id === 6) spotCategory = '2W';
            }
            const spotName = r.parkingSpot ? `Slot #${r.parkingSpot.id} (${spotCategory})` : 'N/A';
            const vehicleText = `${(r.vehicleType || 'vehicle').toUpperCase()} (${r.vehicleNumber || 'N/A'})`;
            const aptNo = r.apartmentNumber ? r.apartmentNumber : 'N/A';
            const uType = r.userType ? r.userType : 'Resident';
            const revenue = typeof r.revenue === 'number' ? r.revenue.toFixed(0) : '0';

            const paymentBadge = isPaid
                ? '<span style="background:rgba(16,185,129,0.1);color:#10b981;padding:4px 8px;border-radius:4px;font-size:0.8rem;font-weight:600;">PAID ✅</span>'
                : r.paymentStatus === 'PENDING'
                    ? '<span style="background:rgba(245,158,11,0.1);color:#f59e0b;padding:4px 8px;border-radius:4px;font-size:0.8rem;font-weight:600;">PENDING ⏳</span>'
                    : '<span style="background:rgba(156,163,175,0.1);color:#9ca3af;padding:4px 8px;border-radius:4px;font-size:0.8rem;font-weight:600;">N/A</span>';

            let receiptLinks = '-';
            if (isPaid) {
                receiptLinks = `
                    <div style="display:flex; flex-direction:column; gap:4px; font-size:0.75rem;">
                        <a href="${API_BASE_URL}/receipts/reservation/${r.id}" target="_blank" style="color:#818cf8; text-decoration:none; font-weight:600;">🧾 View JSON</a>
                        <a href="#" class="print-link" data-res-id="${r.id}" style="color:#10b981; text-decoration:none; font-weight:600;">📄 Print View</a>
                    </div>
                `;
            }

            row.innerHTML = `
                <td><strong>#${r.id}</strong></td>
                <td><span style="color: #818cf8; font-weight: 500;">${spotName}</span></td>
                <td>${r.clientName || 'N/A'}</td>
                <td><small style="color: #9ca3af;">${r.clientEmail || 'N/A'}</small></td>
                <td><span style="font-size: 0.8rem; font-weight:600; color: ${uType.toLowerCase() === 'resident' ? '#3b82f6' : '#f59e0b'};">${uType}</span></td>
                <td><span style="color: #a5b4fc; font-weight: 600;">Unit ${aptNo}</span></td>
                <td><code>${vehicleText}</code></td>
                <td>${r.duration || 0}h</td>
                <td><strong>₹${revenue}</strong></td>
                <td>${paymentBadge}</td>
                <td><span style="${statusStyle}">${(r.status || 'unknown').toUpperCase()}</span></td>
                <td>${receiptLinks}</td>
                <td style="white-space: nowrap;">${actionButtons}</td>
            `;
            tbody.appendChild(row);
        });

    } catch (error) {
        console.error("Logs render error:", error);
        tbody.innerHTML = `<tr><td colspan="13" style="text-align: center; color: #ef4444; padding: 2rem;">❌ Error loading reservation logs. Is backend running?</td></tr>`;
    }
}

// Open the print receipt for a reservation (finds the CUSTOMER receipt)
async function printReceiptForReservation(reservationId) {
    try {
        const response = await fetch(`${API_BASE_URL}/receipts/reservation/${reservationId}`);
        if (!response.ok) throw new Error("No receipts found");
        const receipts = await response.json();
        if (!receipts || receipts.length === 0) {
            alert(`No receipts generated yet for reservation #${reservationId}.`);
            return;
        }
        const customerReceipt = receipts.find(r => r.receiptType === 'CUSTOMER') || receipts[0];
        window.open(`${API_BASE_URL}/receipts/${customerReceipt.id}/html`, '_blank');
    } catch (e) {
        alert(`Error opening receipt: ${e.message}`);
    }
}

async function forceReleaseSpot(reservationId, spotId) {
    try {
        if (!confirm(`Are you sure you want to end reservation #${reservationId} and release Spot #${spotId}?`)) {
            return;
        }
        isPerformingAction = true;
        const response = await fetch(`${API_BASE_URL}/reservations/${reservationId}`, {
            method: 'DELETE'
        });
        if (!response.ok) {
            const msg = await response.text();
            throw new Error(msg || "Failed to complete reservation");
        }
        alert(`✅ Spot #${spotId} has been successfully released.`);
        await fetchAdminData();
    } catch (error) {
        console.error("Force release error:", error);
        alert(`❌ Error releasing spot: ${error.message}`);
    } finally {
        isPerformingAction = false;
    }
}

async function purgeReservationRecord(id) {
    try {
        if (!confirm(`WARNING: This will completely delete reservation log #${id} from the database. If active, it will also free the associated slot. Proceed?`)) {
            return;
        }
        isPerformingAction = true;
        const response = await fetch(`${API_BASE_URL}/reservations/${id}/delete`, {
            method: 'DELETE'
        });
        if (!response.ok) {
            const msg = await response.text();
            throw new Error(msg || "Failed to delete record");
        }
        alert(`✅ Reservation #${id} completely purged from database logs.`);
        await fetchAdminData();
    } catch (error) {
        console.error("Purge error:", error);
        alert(`❌ Error deleting record: ${error.message}`);
    } finally {
        isPerformingAction = false;
    }
}

async function resetDatabase() {
    try {
        if (!confirm("Are you sure you want to purge and reset the database to default seed records? All custom data will be cleared.")) {
            return;
        }
        isPerformingAction = true;
        const response = await fetch(`${API_BASE_URL}/admin/reset`, {
            method: 'POST'
        });
        if (!response.ok) throw new Error("Database reset failed");

        alert("✅ Database successfully reset to default seeded data!");
        await fetchAdminData();
    } catch (error) {
        console.error("DB reset error:", error);
        alert(`❌ Error resetting database: ${error.message}`);
    } finally {
        isPerformingAction = false;
    }
}

function showDbAlert() {
    const mainDiv = document.querySelector('.dashboard');
    if (mainDiv && !document.getElementById('db-admin-alert')) {
        const alertEl = document.createElement('div');
        alertEl.id = 'db-admin-alert';
        alertEl.style.cssText = "padding: 1.25rem; margin-bottom: 2rem; background: rgba(239, 68, 68, 0.1); border: 1px solid #ef4444; color: #ef4444; border-radius: 10px; text-align: center; font-weight:600;";
        alertEl.innerHTML = `<i class="fas fa-exclamation-triangle"></i> Administrative connection error: unable to establish link to CommuniPark server (localhost:8080). Make sure the backend is running.`;
        mainDiv.prepend(alertEl);
    }
}

async function adminConfirmPayment(reservationId) {
    try {
        if (!confirm(`Are you sure you want to mark reservation #${reservationId} as PAID?\n\nThis will:\n✅ Confirm payment\n✅ Generate 3 receipts (Customer, Admin, Lock)\n✅ Send email & WhatsApp notifications\n✅ Update reservation status`)) {
            return;
        }
        isPerformingAction = true;
        const response = await fetch(`${API_BASE_URL}/payment/confirm`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                reservationId: reservationId,
                paymentMethod: 'UPI'
            })
        });
        const data = await response.json();
        if (response.ok && data.status === 'PAID') {
            alert(`✅ Payment Confirmed Successfully!\n\n📋 Receipt: ${data.receiptNumber}\n💰 Amount: ₹${data.amount}\n🔖 Transaction: ${data.transactionId}\n\nReceipts generated: ${data.receiptsGenerated}`);
            await fetchAdminData(); // Refresh UI
        } else {
            const errMsg = data.error || data.message || 'Unknown error from server';
            alert(`❌ Failed to confirm payment:\n${errMsg}`);
        }
    } catch (e) {
        alert(`❌ Network error calling confirm endpoint:\n${e.message}`);
    } finally {
        isPerformingAction = false;
    }
}

// Expose functions globally for HTML onclick event handlers (legacy/fallback)
window.handleAdminLogin = handleAdminLogin;
window.adminLogout = adminLogout;
window.fetchAdminData = fetchAdminData;
window.resetDatabase = resetDatabase;
window.forceReleaseSpot = forceReleaseSpot;
window.purgeReservationRecord = purgeReservationRecord;
window.adminConfirmPayment = adminConfirmPayment;
window.printReceiptForReservation = printReceiptForReservation;
