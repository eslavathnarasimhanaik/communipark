const API_BASE_URL = window.location.hostname === 'localhost' ? 'http://localhost:8080/api' : `${window.location.origin}/api`;
let hourlyTrendChart = null;
let vehicleTypeChart = null;

document.addEventListener("DOMContentLoaded", function() {
    // Fetch data and update dashboard
    fetchDashboardData();
});

async function fetchDashboardData() {
    try {
        const response = await fetch(`${API_BASE_URL}/dashboard/stats`);
        if (!response.ok) throw new Error("Failed to load dashboard stats");
        const data = await response.json();
        
        updateStatistics(data);
        updateCharts(data);
        updateParkingDetails(data);
    } catch (error) {
        console.error("Error fetching dashboard data:", error);
        // Display fallback or error message
        const container = document.querySelector('.dashboard');
        if (container && !document.getElementById('db-error-alert')) {
            const alert = document.createElement('div');
            alert.id = 'db-error-alert';
            alert.style.cssText = "padding: 1rem; margin-bottom: 2rem; background: rgba(239, 68, 68, 0.1); border: 1px solid #ef4444; color: #ef4444; border-radius: 8px; text-align: center;";
            alert.innerHTML = `<i class="fas fa-exclamation-triangle"></i> Cannot connect to the Java backend server. Please verify it is running on port 8080.`;
            container.prepend(alert);
        }
    }
}

// Update statistics cards
function updateStatistics(data) {
    const occupancyEl = document.getElementById("occupancy-rate");
    const peakEl = document.getElementById("peak-hour");
    const avgDurEl = document.getElementById("average-duration");
    const revEl = document.getElementById("daily-revenue");

    if (occupancyEl) occupancyEl.textContent = data.occupancyRate;
    if (peakEl) peakEl.textContent = data.peakHour;
    if (avgDurEl) avgDurEl.textContent = data.averageDuration;
    if (revEl) revEl.textContent = data.dailyRevenue;
}

// Update charts using Chart.js
function updateCharts(data) {
    // 1. Hourly Trend Chart
    const hourlyTrendCanvas = document.getElementById("hourlyTrendChart");
    if (hourlyTrendCanvas) {
        const hourlyTrendCtx = hourlyTrendCanvas.getContext("2d");
        
        // Destroy existing chart if it exists to prevent overlapping/glitches on refresh
        if (hourlyTrendChart) {
            hourlyTrendChart.destroy();
        }

        hourlyTrendChart = new Chart(hourlyTrendCtx, {
            type: "line",
            data: {
                labels: ["8 AM", "9 AM", "10 AM", "11 AM", "12 PM", "1 PM", "2 PM", "3 PM", "4 PM", "5 PM"],
                datasets: [{
                    label: "Occupancy Trend (%)",
                    data: data.hourlyTrend,
                    borderColor: "#3b82f6",
                    backgroundColor: "rgba(59, 130, 246, 0.15)",
                    tension: 0.3,
                    fill: true,
                    borderWidth: 2
                }]
            },
            options: {
                responsive: true,
                maintainAspectRatio: false,
                plugins: {
                    legend: {
                        display: false
                    }
                },
                scales: {
                    y: {
                        beginAtZero: true,
                        max: 100,
                        grid: {
                            color: "rgba(226, 232, 240, 0.1)"
                        }
                    },
                    x: {
                        grid: {
                            display: false
                        }
                    }
                }
            }
        });
    }

    // 2. Vehicle Type Chart
    const vehicleTypeCanvas = document.getElementById("vehicleTypeChart");
    if (vehicleTypeCanvas) {
        const vehicleTypeCtx = vehicleTypeCanvas.getContext("2d");

        if (vehicleTypeChart) {
            vehicleTypeChart.destroy();
        }

        vehicleTypeChart = new Chart(vehicleTypeCtx, {
            type: "doughnut",
            data: {
                labels: ["Cars", "Bikes", "Trucks"],
                datasets: [{
                    label: "Vehicle Type Distribution",
                    data: data.vehicleTypeDistribution,
                    backgroundColor: ["#3b82f6", "#10b981", "#f59e0b"],
                    borderWidth: 0
                }]
            },
            options: {
                responsive: true,
                maintainAspectRatio: false,
                plugins: {
                    legend: {
                        position: 'bottom',
                        labels: {
                            boxWidth: 12,
                            padding: 15
                        }
                    }
                }
            }
        });
    }
}

// Update parking details table
function updateParkingDetails(data) {
    const tbody = document.getElementById("parking-details");
    if (!tbody) return;
    
    // Check if header needs Action column
    const tableHeader = tbody.closest('table').querySelector('thead tr');
    if (tableHeader && !tableHeader.querySelector('.action-header')) {
        const actionTh = document.createElement('th');
        actionTh.className = 'action-header';
        actionTh.textContent = 'Action';
        tableHeader.appendChild(actionTh);
    }

    tbody.innerHTML = ""; // Clear existing rows
    
    data.parkingDetails.forEach(detail => {
        const row = document.createElement("tr");
        const isOccupied = detail.status.toLowerCase() === "occupied";
        const statusClass = isOccupied ? "color: #ef4444; font-weight: 600;" : "color: #10b981; font-weight: 600;";
        
        let actionButtonHTML = '';
        if (isOccupied && detail.reservationId) {
            actionButtonHTML = `
                <td>
                    <button class="release-btn" onclick="releaseParkingSpot(${detail.reservationId}, ${detail.id})" style="background-color: #ef4444; padding: 4px 8px; font-size: 0.8rem; border-radius: 4px; border: none; color: white; cursor: pointer; transition: background 0.2s;">
                        <i class="fas fa-sign-out-alt"></i> Release
                    </button>
                </td>
            `;
        } else {
            actionButtonHTML = `<td><span style="color: #6b7280; font-size: 0.9rem;">-</span></td>`;
        }

        const uTypeVal = detail.userType !== 'N/A' ? detail.userType : '-';
        const aptVal = detail.apartmentNumber !== 'N/A' ? 'Unit ' + detail.apartmentNumber : '-';

        row.innerHTML = `
            <td><strong>Slot #${detail.id}</strong></td>
            <td>${detail.type}</td>
            <td><span style="${statusClass}">${detail.status}</span></td>
            <td><span style="font-weight: 600; color: ${uTypeVal.toLowerCase() === 'resident' ? '#3b82f6' : '#f59e0b'};">${uTypeVal}</span></td>
            <td><span style="color: #a5b4fc; font-weight: 500;">${aptVal}</span></td>
            <td>${detail.duration}</td>
            <td>${detail.vehicle}</td>
            <td><strong>${detail.revenue}</strong></td>
            ${actionButtonHTML}
        `;
        tbody.appendChild(row);
    });
}

// Release spot (Complete reservation)
async function releaseParkingSpot(reservationId, spotId) {
    if (!confirm(`Are you sure you want to release Spot #${spotId}? This will end the reservation.`)) {
        return;
    }

    try {
        const response = await fetch(`${API_BASE_URL}/reservations/${reservationId}`, {
            method: 'DELETE'
        });

        if (!response.ok) {
            throw new Error("Failed to complete reservation");
        }

        // Show a brief success alert or notification
        alert(`Spot #${spotId} is now available!`);
        
        // Refresh dashboard data
        fetchDashboardData();
    } catch (error) {
        console.error("Error completing reservation:", error);
        alert(`Failed to release spot: ${error.message}`);
    }
}
