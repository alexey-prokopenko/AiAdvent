#!/usr/bin/env node

/**
 * HTTP Proxy для MCP сервера
 * 
 * Этот скрипт запускает MCP сервер через stdio и предоставляет HTTP API
 * для подключения с Android устройств.
 * 
 * Использование:
 *   node mcp-http-proxy.js [port] [mcp-server-command] [args...]
 * 
 * Примеры:
 *   node mcp-http-proxy.js 3000 npx -y @modelcontextprotocol/server-everything
 *   node mcp-http-proxy.js 3000 npx -y @modelcontextprotocol/server-filesystem /tmp
 */

const http = require('http');
const { spawn } = require('child_process');

const PORT = process.argv[2] ? parseInt(process.argv[2]) : 3000;
const MCP_COMMAND = process.argv[3] || 'npx';
const MCP_ARGS = process.argv.slice(4).length > 0 
    ? process.argv.slice(4) 
    : ['-y', '@modelcontextprotocol/server-everything'];

let requestId = 1;
let pendingRequests = new Map(); // id -> { resolve, reject, clientId }
let clientIdToMcpId = new Map(); // clientId -> mcpId

console.log(`🚀 Запуск MCP HTTP Proxy на порту ${PORT}`);
console.log(`📡 MCP команда: ${MCP_COMMAND} ${MCP_ARGS.join(' ')}`);

// Запускаем MCP сервер
// Передаем переменные окружения
const mcpProcess = spawn(MCP_COMMAND, MCP_ARGS, {
    stdio: ['pipe', 'pipe', 'pipe'],
    env: { ...process.env } // Передаем все переменные окружения
});

mcpProcess.stdout.setEncoding('utf8');
mcpProcess.stderr.setEncoding('utf8');

// Обработка вывода от MCP сервера
let buffer = '';
mcpProcess.stdout.on('data', (data) => {
    // Логируем сырые данные для отладки
    // console.log('📦 Сырые данные от MCP:', data.substring(0, 500));
    buffer += data;
    const lines = buffer.split('\n');
    buffer = lines.pop() || ''; // Оставляем неполную строку в буфере
    
    for (const line of lines) {
        if (line.trim()) {
            try {
                const response = JSON.parse(line);
                // Логируем все ответы для отладки
                if (response.id !== null && response.id !== undefined) {
                    console.log(`📥 Получен ответ с mcpId: ${response.id}`);
                    if (pendingRequests.has(response.id)) {
                        const { resolve, reject, clientId } = pendingRequests.get(response.id);
                        pendingRequests.delete(response.id);
                        
                        // Если есть clientId, заменяем id в ответе на clientId
                        if (clientId !== null) {
                            const originalMcpId = response.id;
                            response.id = clientId;
                            clientIdToMcpId.delete(clientId);
                            console.log(`✅ Ответ для mcpId ${originalMcpId} обработан, заменён на clientId ${clientId}`);
                        } else {
                            console.log(`✅ Ответ для mcpId ${response.id} обработан`);
                        }
                        resolve(response);
                    } else {
                        console.log(`⚠️ Ответ с mcpId ${response.id} не найден в pendingRequests`);
                    }
                } else if (response.id === null || response.id === undefined) {
                    // Это уведомление от сервера - просто логируем
                    console.log('📨 Уведомление от MCP сервера:', response.method || 'unknown');
                }
            } catch (e) {
                // Логируем не-JSON строки для отладки
                if (line.trim().length > 0 && !line.trim().startsWith('MCP stderr:')) {
                    console.log('⚠️ Не-JSON строка от MCP:', line.substring(0, 200));
                }
            }
        }
    }
});

mcpProcess.stderr.on('data', (data) => {
    console.error('MCP stderr:', data);
});

mcpProcess.on('error', (error) => {
    console.error('❌ Ошибка запуска MCP сервера:', error.message);
    process.exit(1);
});

mcpProcess.on('exit', (code) => {
    console.error(`⚠️ MCP сервер завершился с кодом ${code}`);
    process.exit(code);
});

// Функция для отправки запроса к MCP серверу
function sendMcpRequest(method, params = null, clientId = null) {
    return new Promise((resolve, reject) => {
        const mcpId = requestId++;
        const request = {
            jsonrpc: '2.0',
            id: mcpId,
            method: method,
            params: params
        };
        
        // Сохраняем соответствие между clientId и mcpId
        if (clientId !== null) {
            clientIdToMcpId.set(clientId, mcpId);
        }
        
        pendingRequests.set(mcpId, { resolve, reject, clientId });
        
        // Устанавливаем таймаут (увеличено до 60 секунд для инициализации)
        setTimeout(() => {
            if (pendingRequests.has(mcpId)) {
                pendingRequests.delete(mcpId);
                if (clientId !== null) {
                    clientIdToMcpId.delete(clientId);
                }
                reject(new Error('Timeout waiting for MCP response (60s)'));
            }
        }, 60000);
        
        const requestStr = JSON.stringify(request) + '\n';
        console.log(`📨 Отправка к MCP: ${method} (mcpId: ${mcpId}${clientId !== null ? `, clientId: ${clientId}` : ''})`);
        mcpProcess.stdin.write(requestStr, (error) => {
            if (error) {
                console.error(`❌ Ошибка записи в stdin для ${method}:`, error);
                pendingRequests.delete(mcpId);
                if (clientId !== null) {
                    clientIdToMcpId.delete(clientId);
                }
                reject(error);
            } else {
                console.log(`✅ Запрос ${method} (mcpId: ${mcpId}) отправлен в stdin`);
            }
        });
    });
}

// Инициализация MCP сервера
async function initializeMcp() {
    try {
        console.log('🔄 Инициализация MCP сервера...');
        await sendMcpRequest('initialize', {
            protocolVersion: '2024-11-05',
            capabilities: {},
            clientInfo: {
                name: 'mcp-http-proxy',
                version: '1.0.0'
            }
        });
        
        // После initialize нужно отправить initialized (notification без id)
        console.log('📤 Отправка initialized (notification)...');
        const initializedNotification = JSON.stringify({
            jsonrpc: '2.0',
            method: 'initialized'
        }) + '\n';
        mcpProcess.stdin.write(initializedNotification, (error) => {
            if (error) {
                console.error('Ошибка отправки initialized:', error);
            } else {
                console.log('✅ initialized отправлен как notification');
            }
        });
        
        // Небольшая задержка после инициализации
        await new Promise(resolve => setTimeout(resolve, 500));
        
        console.log('✅ MCP сервер инициализирован');
    } catch (error) {
        console.error('❌ Ошибка инициализации:', error.message);
        process.exit(1);
    }
}

// Создаем HTTP сервер
const server = http.createServer(async (req, res) => {
    // CORS заголовки
    res.setHeader('Access-Control-Allow-Origin', '*');
    res.setHeader('Access-Control-Allow-Methods', 'POST, OPTIONS');
    res.setHeader('Access-Control-Allow-Headers', 'Content-Type');
    res.setHeader('Content-Type', 'application/json');
    
    if (req.method === 'OPTIONS') {
        res.writeHead(200);
        res.end();
        return;
    }
    
    if (req.method !== 'POST') {
        res.writeHead(405);
        res.end(JSON.stringify({ error: 'Method not allowed' }));
        return;
    }
    
    let body = '';
    req.on('data', chunk => {
        body += chunk.toString();
    });
    
    req.on('end', async () => {
        try {
            const request = JSON.parse(body);
            
            // Если это уведомление (без id), отправляем и сразу отвечаем
            if (request.id === null || request.id === undefined) {
                // Отправляем уведомление к MCP серверу (не ждём ответа)
                const requestStr = JSON.stringify(request) + '\n';
                try {
                    mcpProcess.stdin.write(requestStr, (error) => {
                        if (error) {
                            console.error('Ошибка отправки уведомления:', error);
                        } else {
                            console.log('✅ Уведомление отправлено:', request.method);
                        }
                    });
                } catch (error) {
                    console.error('Ошибка записи в stdin:', error);
                }
                
                // Для уведомлений сразу отвечаем успехом (без ожидания ответа от MCP)
                res.writeHead(200, { 'Content-Type': 'application/json' });
                res.end(JSON.stringify({ jsonrpc: '2.0', result: null }));
                return;
            }
            
            // Для обычных запросов ждём ответа
            console.log(`📤 Запрос: ${request.method} (clientId: ${request.id})`);
            try {
                const response = await sendMcpRequest(request.method, request.params, request.id);
                console.log(`✅ Ответ получен для ${request.method} (clientId: ${request.id})`);
                res.writeHead(200, { 'Content-Type': 'application/json' });
                res.end(JSON.stringify(response));
            } catch (error) {
                console.error(`❌ Ошибка при обработке ${request.method}:`, error.message);
                throw error;
            }
        } catch (error) {
            res.writeHead(500);
            res.end(JSON.stringify({
                jsonrpc: '2.0',
                id: null,
                error: {
                    code: -32603,
                    message: error.message
                }
            }));
        }
    });
});

// Запускаем сервер
server.listen(PORT, '0.0.0.0', async () => {
    console.log(`✅ HTTP Proxy запущен на http://0.0.0.0:${PORT}`);
    console.log(`📱 Для подключения с Android используйте: http://YOUR_IP:${PORT}`);
    console.log(`   (Замените YOUR_IP на IP адрес вашего компьютера)`);
    console.log('');
    
    // Инициализируем MCP сервер после запуска HTTP сервера
    await initializeMcp();
    console.log('🎉 Готово к работе!');
});

// Обработка завершения
process.on('SIGINT', () => {
    console.log('\n🛑 Остановка сервера...');
    mcpProcess.kill();
    server.close();
    process.exit(0);
});

