const API_BASE_URL = window.location.hostname === 'localhost' ? 'http://localhost:8080/api' : `${window.location.origin}/api`;
let parkingSlots = [];
let selectedSlot = null;

async function fetchParkingSlots() {
    try {
        const response = await fetch(`${API_BASE_URL}/parking-spots`);
        if (!response.ok) throw new Error("Failed to load parking spots");
        parkingSlots = await response.json();
        renderParkingGrid();
    } catch (error) {
        console.error("Error loading parking spots:", error);
        // Show status on page
        const grid = document.getElementById('parkingGrid');
        if (grid) {
            grid.innerHTML = `
                <div class="error-state" style="grid-column: 1 / -1; text-align: center; padding: 2rem; background: rgba(239, 68, 68, 0.1); border: 1px solid rgba(239, 68, 68, 0.2); border-radius: 8px;">
                    <i class="fas fa-exclamation-triangle" style="font-size: 2rem; color: #ef4444; margin-bottom: 0.5rem;"></i>
                    <h3 style="color: #ef4444;">Connection Error</h3>
                    <p style="color: #6b7280; margin-top: 0.25rem;">Unable to connect to the parking server at ${API_BASE_URL}. Please ensure the Java Spring Boot backend is running.</p>
                </div>
            `;
        }
    }
}

function getIndianCategoryName(slot) {
    if (slot.type === 'vip') return "VIP Owner";
    if (slot.id === 3 || slot.id === 6) return "2W (Two-Wheeler)";
    return "4W (Four-Wheeler)";
}

function renderParkingGrid() {
    const parkingGrid = document.getElementById('parkingGrid');
    if (!parkingGrid) return;
    parkingGrid.innerHTML = '';

    parkingSlots.forEach(slot => {
        const slotElement = document.createElement('div');
        const isOccupied = slot.status === 'occupied';
        const indianCategory = getIndianCategoryName(slot);
        slotElement.className = `parking-slot ${isOccupied ? 'occupied' : ''} ${slot.type} ${slot.id === 3 || slot.id === 6 ? 'two-wheeler' : ''}`;
        slotElement.innerHTML = `
            <h3>Slot #${slot.id}</h3>
            <span class="spot-type-badge">${indianCategory}</span>
            ${slot.type === 'vip' ? '<i class="fas fa-star vip-star"></i>' : ''}
            <div class="spot-status-text">${isOccupied ? 'OCCUPIED' : 'AVAILABLE'}</div>
        `;
        slotElement.addEventListener('click', () => handleSlotClick(slot));
        parkingGrid.appendChild(slotElement);
    });

    updateAvailableSpacesCount();
}

function handleSlotClick(slot) {
    if (slot.status === 'available') {
        selectedSlot = slot;
        document.getElementById('overlay').style.display = 'block';
        document.getElementById('reservationForm').style.display = 'block';
        
        // Reset or pre-fill form inputs from localStorage profile
        document.getElementById('vehicleNumber').value = localStorage.getItem('profileVehicle') || '';
        document.getElementById('duration').value = '1';
        
        const userTypeEl = document.getElementById('userType');
        if (userTypeEl) {
            userTypeEl.value = localStorage.getItem('profileUserType') || 'Resident';
        }
        
        const apartmentEl = document.getElementById('apartmentNumber');
        if (apartmentEl) {
            apartmentEl.value = localStorage.getItem('profileApartment') || '';
        }

        // Set min booking date to tomorrow
        const bookingDateEl = document.getElementById('bookingDate');
        if (bookingDateEl) {
            const tomorrow = new Date();
            tomorrow.setDate(tomorrow.getDate() + 1);
            bookingDateEl.min = tomorrow.toISOString().split('T')[0];
            bookingDateEl.value = tomorrow.toISOString().split('T')[0];
        }

        // Pre-fill phone
        const phoneEl = document.getElementById('phoneNumber');
        if (phoneEl) {
            phoneEl.value = localStorage.getItem('profilePhone') || '';
        }
    } else {
        showConfirmation(`Spot #${slot.id} is currently occupied!`, true);
    }
}

function closeForm() {
    document.getElementById('overlay').style.display = 'none';
    document.getElementById('reservationForm').style.display = 'none';
    selectedSlot = null;
}

function updateAvailableSpacesCount() {
    const available = parkingSlots.filter(slot => slot.status === 'available').length;
    const availableSpacesEl = document.getElementById('available-spaces');
    if (availableSpacesEl) {
        availableSpacesEl.textContent = available;
    }
}

function showConfirmation(message, isError = false) {
    const msgEl = document.getElementById('confirmation-message');
    if (msgEl) {
        msgEl.textContent = message;
        msgEl.style.display = 'block';
        msgEl.style.color = isError ? '#ef4444' : '#10b981';
        
        // Scroll to the message
        msgEl.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
        
        // Auto hide after 5 seconds
        setTimeout(() => {
            msgEl.style.display = 'none';
        }, 5000);
    }
}

// Form Submission handling
const formContent = document.getElementById('reservationFormContent');
if (formContent) {
    formContent.addEventListener('submit', async (e) => {
        e.preventDefault();
        
        if (!selectedSlot) return;

        const vehicleNumber = document.getElementById('vehicleNumber').value;
        const vehicleType = document.getElementById('vehicleType').value;
        const userType = document.getElementById('userType').value;
        const apartmentNumber = document.getElementById('apartmentNumber').value;
        const duration = parseInt(document.getElementById('duration').value);
        const bookingDate = document.getElementById('bookingDate').value;
        const phoneNumber = document.getElementById('phoneNumber').value;
        const paymentMethod = document.getElementById('paymentMethod').value;

        const clientName = localStorage.getItem('profileName') || "Resident Driver";
        const clientEmail = localStorage.getItem('profileEmail') || "quick@parking.com";

        // Save phone to localStorage
        if (phoneNumber) localStorage.setItem('profilePhone', phoneNumber);

        if (vehicleNumber && duration && bookingDate) {
            try {
                const response = await fetch(`${API_BASE_URL}/reservations/spot/${selectedSlot.id}`, {
                    method: 'POST',
                    headers: {
                        'Content-Type': 'application/json',
                    },
                    body: JSON.stringify({
                        vehicleNumber,
                        vehicleType,
                        userType,
                        apartmentNumber,
                        duration,
                        clientName,
                        clientEmail,
                        phoneNumber,
                        bookingDate,
                        paymentMethod
                    })
                });

                if (!response.ok) {
                    const errMsg = await response.text();
                    throw new Error(errMsg || 'Failed to book spot');
                }

                const reservation = await response.json();
                closeForm();
                
                // Redirect to payment page
                window.location.href = `payment.html?resId=${reservation.id}`;
            } catch (err) {
                console.error("Booking error:", err);
                showConfirmation(`Booking failed: ${err.message}`, true);
            }
        }
    });
}

// Initial load
document.addEventListener("DOMContentLoaded", () => {
    fetchParkingSlots();
});
