const tickets = [
  {
    id: 101,
    subject: "Login issue",
    status: "open",
    priority: "high",
    requester_id: 201,
    description: "User cannot log in to the system."
  },
  {
    id: 102,
    subject: "Feature request: Dark mode",
    status: "pending",
    priority: "normal",
    requester_id: 202,
    description: "Please add a dark mode option."
  },
  {
    id: 103,
    subject: "Bug: Page not loading",
    status: "solved",
    priority: "urgent",
    requester_id: 203,
    description: "The dashboard page fails to load for some users."
  }
]

const mockData = {
  "/api/v2/tickets": {
    get: {
      count: 3,
      tickets: tickets,
    }
  },
  "/api/v2/tickets/{id}.json": {
    get: {
      "101": tickets.find(ticket => ticket.id === 101),
      "102": tickets.find(ticket => ticket.id === 102),
      "103": tickets.find(ticket => ticket.id === 103)
    }
  }
}

module.exports = mockData