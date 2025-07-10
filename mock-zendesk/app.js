const express = require('express');
const fs = require('fs');
const path = require('path');
const yaml = require('js-yaml');

const mockData = require('./mock-data.js');

const app = express();
const port = 3000;

app.use(express.json());

const openApiPath = path.join(__dirname, 'oas.yaml');
const openApiSpec = fs.readFileSync(openApiPath, 'utf8');
const openApiDoc = yaml.load(openApiSpec);	

app.get('/api/v2/tickets', (req, res) => {
	const tickets = mockData['/api/v2/tickets'].get.tickets;
	res.json(tickets);
})

app.get('/api/v2/tickets/:id.json', (req, res) => {
	const ticket = mockData[`/api/v2/tickets/{id}.json`]?.get[req.params.id];
	res.json(ticket);
});

app.listen(port, '0.0.0.0', () => {
	console.log(`App listening on port ${port}`);
});