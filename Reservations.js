const API_BASE_URL = window.API_BASE_URL || 'http://localhost:8080/api';

document.addEventListener("DOMContentLoaded", () => {
    // Set default date and time to current local values
    setDefaultDateTime();
    
    // Pre-fill profile details if present
    const nameInput = document.getElementById('name');
    const emailInput = document.getElementById('email');
    const vehInput = document.getElementById('vehicleNumber');
    const userTypeInput = document.getElementById('userType');
    const aptInput = document.getElementById('apartmentNumber');

    if (nameInput) nameInput.value = localStorage.getItem('profileName') || '';
    if (emailInput) emailInput.value = localStorage.getItem('profileEmail') || '';
    if (vehInput) vehInput.value = localStorage.getItem('profileVehicle') || '';
    if (userTypeInput) userTypeInput.value = localStorage.getItem('profileUserType') || 'Resident';
    if (aptInput) aptInput.value = localStorage.getItem('profileApartment') || '';

    const phoneInput = document.getElementById('phoneNumber');
    if (phoneInput) phoneInput.value = localStorage.getItem('profilePhone') || '';
    
    // Fetch and render existing reservations
    fetchAndRenderReservations();

    // Handle form submission
    const form = document.getElementById('booking-form');
    if (form) {
        form.addEventListener('submit', handleFormSubmit);
    }
});

function setDefaultDateTime() {
    const dateInput = document.getElementById('date');
    const timeInput = document.getElementById('time');
    
    if (dateInput && timeInput) {
        const now = new Date();
        
        // Format YYYY-MM-DD
        const year = now.getFullYear();
        const month = String(now.getMonth() + 1).padStart(2, '0');
        const day = String(now.getDate()).padStart(2, '0');
        dateInput.value = `${year}-${month}-${day}`;
        // Set min to tomorrow for advance booking
        const tomorrow = new Date();
        tomorrow.setDate(tomorrow.getDate() + 1);
        const ty = tomorrow.getFullYear();
        const tm = String(tomorrow.getMonth() + 1).padStart(2, '0');
        const td = String(tomorrow.getDate()).padStart(2, '0');
        dateInput.min = `${ty}-${tm}-${td}`;
        dateInput.value = `${ty}-${tm}-${td}`;
        
        // Format HH:MM
        const hours = String(now.getHours()).padStart(2, '0');
        const minutes = String(now.getMinutes()).padStart(2, '0');
        timeInput.value = `${hours}:${minutes}`;
    }
}

async function fetchAndRenderReservations() {
    const tbody = document.getElementById('reservations-table-body');
    if (!tbody) return;

    try {
        const response = await fetch(`${API_BASE_URL}/reservations`);
        if (!response.ok) throw new Error("Failed to fetch reservations");
        
        const reservations = await response.json();
        
        // Sort: Active first, then by ID descending (newest first)
        reservations.sort((a, b) => {
            if (a.status === 'active' && b.status !== 'active') return -1;
            if (a.status !== 'active' && b.status === 'active') return 1;
            return b.id - a.id;
        });

        tbody.innerHTML = '';

        if (reservations.length === 0) {
            tbody.innerHTML = `<tr><td colspan="9" style="text-align: center; color: #6b7280; padding: 2rem;">No reservations found.</td></tr>`;
            return;
        }

        reservations.forEach(res => {
            const row = document.createElement('tr');
            
            const isCompleted = res.status.toLowerCase() === 'completed';
            const statusStyle = isCompleted 
                ? 'background: rgba(16, 185, 129, 0.1); color: #10b981; padding: 4px 8px; border-radius: 4px; font-weight: 600;' 
                : 'background: rgba(59, 130, 246, 0.1); color: #3b82f6; padding: 4px 8px; border-radius: 4px; font-weight: 600;';

            let actionHtml = '-';
            if (!isCompleted) {
                actionHtml = `
                    <button class="release-btn" onclick="cancelReservation(${res.id}, ${res.parkingSpot ? res.parkingSpot.id : 0})" style="background-color: #ef4444; color: white; border: none; padding: 6px 12px; border-radius: 4px; cursor: pointer; font-size: 0.85rem; transition: background 0.2s;">
                        <i class="fas fa-times-circle"></i> Release
                    </button>
                `;
            }

            let spotCategory = '4W';
            if (res.parkingSpot) {
                if (res.parkingSpot.type === 'vip') spotCategory = 'VIP Owner';
                else if (res.parkingSpot.id === 3 || res.parkingSpot.id === 6) spotCategory = '2W';
            }
            const spotName = res.parkingSpot ? `Slot #${res.parkingSpot.id} (${spotCategory})` : 'N/A';
            const costVal = res.revenue ? `₹${res.revenue.toFixed(0)}` : '₹0';
            
            // Format vehicle type icon
            let typeIcon = '🚗';
            if (res.vehicleType === 'motorcycle') typeIcon = '🏍️';
            else if (res.vehicleType === 'truck') typeIcon = '🚛';

            const uTypeVal = res.userType ? res.userType : 'Resident';
            const uTypeStyle = uTypeVal.toLowerCase() === 'resident'
                ? 'background: rgba(59, 130, 246, 0.1); color: #3b82f6; padding: 4px 8px; border-radius: 4px; font-size: 0.8rem; font-weight: 600;'
                : 'background: rgba(245, 158, 11, 0.1); color: #f59e0b; padding: 4px 8px; border-radius: 4px; font-size: 0.8rem; font-weight: 600;';
            const aptVal = res.apartmentNumber ? res.apartmentNumber : 'N/A';

            const paymentBadge = res.paymentStatus === 'PAID'
                ? '<span style="background:rgba(16,185,129,0.1);color:#10b981;padding:4px 8px;border-radius:4px;font-size:0.8rem;font-weight:600;">PAID ✅</span>'
                : '<span style="background:rgba(245,158,11,0.1);color:#f59e0b;padding:4px 8px;border-radius:4px;font-size:0.8rem;font-weight:600;">PENDING ⏳</span>';
            
            let receiptHtml = '-';
            if (res.paymentStatus === 'PAID' || res.receiptNumber) {
                receiptHtml = `<a href="${API_BASE_URL}/receipts/reservation/${res.id}" target="_blank" style="color:#818cf8;font-weight:600;text-decoration:none;font-size:0.85rem;">🧾 View</a>`;
            } else {
                receiptHtml = `<a href="payment.html?resId=${res.id}" style="color:#f59e0b;font-weight:600;text-decoration:none;font-size:0.85rem;">💳 Pay Now</a>`;
            }

            let exitCodeHtml = '-';
            if (res.paymentStatus === 'PAID') {
                if (res.exitCode) {
                    if (res.exited) {
                        exitCodeHtml = `<span style="color:#6b7280;text-decoration:line-through;font-family:monospace;font-weight:bold;">${res.exitCode}</span> <small style="display:block;color:#ef4444;font-size:10px;">(Used)</small>`;
                    } else {
                        exitCodeHtml = `<a href="exit.html?code=${res.exitCode}" style="color:#10b981;font-family:monospace;font-weight:bold;text-decoration:none;background:rgba(16,185,129,0.1);padding:4px 8px;border-radius:4px;display:inline-block;box-shadow: 0 0 5px rgba(16,185,129,0.1);"> ${res.exitCode} 🔑</a>`;
                    }
                } else {
                    exitCodeHtml = `<span style="color:#ef4444;font-size:0.8rem;">Pending exit code</span>`;
                }
            } else if (res.status === 'cancelled') {
                exitCodeHtml = `<span style="color:#6b7280;">-</span>`;
            } else {
                exitCodeHtml = `<span style="color:#94a3b8;font-size:0.8rem;">Unpaid</span>`;
            }

            row.innerHTML = `
                <td><strong>#${res.id}</strong></td>
                <td><span style="color: #6366f1; font-weight: 500;">${spotName}</span></td>
                <td>${res.clientName}</td>
                <td><span style="${uTypeStyle}">${uTypeVal}</span></td>
                <td><span style="font-weight: 600; color: #a5b4fc;">Unit ${aptVal}</span></td>
                <td>${typeIcon} ${res.vehicleType.toUpperCase()}</td>
                <td><code>${res.vehicleNumber}</code></td>
                <td>${res.duration} hours</td>
                <td><strong>${costVal}</strong></td>
                <td>${paymentBadge}</td>
                <td><span style="${statusStyle}">${res.status.toUpperCase()}</span></td>
                <td>${receiptHtml}</td>
                <td>${exitCodeHtml}</td>
                <td>${actionHtml}</td>
            `;
            
            tbody.appendChild(row);
        });
    } catch (error) {
        console.error("Error loading reservations:", error);
        tbody.innerHTML = `<tr><td colspan="13" style="text-align: center; color: #ef4444; padding: 2rem;">Error loading reservations. Is the server running?</td></tr>`;
    }
}

async function handleFormSubmit(e) {
    e.preventDefault();

    const name = document.getElementById('name').value;
    const email = document.getElementById('email').value;
    const vehicleNumber = document.getElementById('vehicleNumber').value;
    const vehicle = document.getElementById('vehicle').value;
    const userType = document.getElementById('userType').value;
    const apartmentNumber = document.getElementById('apartmentNumber').value;
    const duration = parseInt(document.getElementById('duration').value);
    const bookingDate = document.getElementById('date').value;
    const phoneNumber = document.getElementById('phoneNumber') ? document.getElementById('phoneNumber').value : '';
    const paymentMethod = document.getElementById('paymentMethod') ? document.getElementById('paymentMethod').value : 'UPI';

    if (phoneNumber) localStorage.setItem('profilePhone', phoneNumber);

    const payload = {
        clientName: name,
        clientEmail: email,
        vehicleNumber: vehicleNumber,
        vehicleType: vehicle,
        userType: userType,
        apartmentNumber: apartmentNumber,
        duration: duration,
        bookingDate: bookingDate,
        phoneNumber: phoneNumber,
        paymentMethod: paymentMethod
    };

    const confirmMsg = document.getElementById('confirmation-message');

    try {
        const response = await fetch(`${API_BASE_URL}/reservations`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify(payload)
        });

        if (!response.ok) {
            const err = await response.text();
            throw new Error(err || "Failed to make reservation");
        }

        const res = await response.json();
        
        // Show success message
        if (confirmMsg) {
            let spotCategory = '4W';
            if (res.parkingSpot) {
                if (res.parkingSpot.type === 'vip') spotCategory = 'VIP Owner';
                else if (res.parkingSpot.id === 3 || res.parkingSpot.id === 6) spotCategory = '2W';
            }
            confirmMsg.textContent = `✅ Booked ${spotCategory} Slot #${res.parkingSpot ? res.parkingSpot.id : ''}! Redirecting to payment...`;
            confirmMsg.style.display = 'block';
            confirmMsg.style.color = '#10b981';
        }
        // Redirect to payment page
        window.location.href = `payment.html?resId=${res.id}`;
    } catch (error) {
        console.error("Error creating reservation:", error);
        if (confirmMsg) {
            confirmMsg.textContent = `Reservation failed: ${error.message}`;
            confirmMsg.style.display = 'block';
            confirmMsg.style.color = '#ef4444';
            
            setTimeout(() => {
                confirmMsg.style.display = 'none';
            }, 6000);
        }
    }
}

// Cancel / Release a booking
async function cancelReservation(id, spotId) {
    if (!confirm(`Are you sure you want to release Spot #${spotId}?`)) {
        return;
    }

    try {
        const response = await fetch(`${API_BASE_URL}/reservations/${id}`, {
            method: 'DELETE'
        });

        if (!response.ok) {
            throw new Error("Failed to cancel reservation");
        }

        // Refresh table
        fetchAndRenderReservations();
    } catch (error) {
        console.error("Cancellation error:", error);
        alert(`Failed to cancel reservation: ${error.message}`);
    }
}

// Expose cancelReservation globally so it can be called from onclick handlers in table
window.cancelReservation = cancelReservation;
