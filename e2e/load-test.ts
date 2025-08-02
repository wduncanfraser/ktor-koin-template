import { check } from 'k6';
import http from 'k6/http';
import { TodoClient } from './generated/todo.ts';
import { uuidv4 } from 'https://jslib.k6.io/k6-utils/1.4.0/index.js';

const baseUrl: string = __ENV.BASE_URL ?? "http://localhost:8080/api/v1";
const cookie: string = __ENV.COOKIE
const todoClient = new TodoClient({ baseUrl });

export const options = {
  vus: 500,
  duration: '60s',
  minIterationDuration: '1s',
};

export default function() {
  const jar = http.cookieJar();
  jar.set('http://localhost:8080', 'example-cookie-id', cookie);

  // List up front
  const initialListResponse = todoClient.listTodos(
      null,
      { tags: { name: "ListTodosURL" } }
  );
  check(initialListResponse.response,
      { "Initial list status is 200": (res) => res.status === 200 }
  );

  // Create a todo
  const createTodoRequest = {
    name: "Cook dinner",
  };

  const createTodoResponse = todoClient.createTodo(
    createTodoRequest,
    { tags: { name: "CreateTodoURL" } }
  );
  check(createTodoResponse.response,
      { "Create status is 200": (res) => res.status === 200 }
  );
  const todoId = createTodoResponse.data.id;

  // Update Todo
  const updateTodoRequest = {
    name: createTodoRequest.name,
    completed: true,
  };
  const updateTodoResponse = todoClient.updateTodo(
    todoId, updateTodoRequest,
    { tags: { name: "UpdateTodoURL" } },
  );
  check(updateTodoResponse.response,
      { "Update todo status is 200": (res) => res.status === 200 }
  );

  // Update a todo that doesn't exist
  const invalidTodoId = uuidv4();
  const invalidUpdateTodoResponse = todoClient.updateTodo(
      invalidTodoId,
      updateTodoRequest,
      { tags: { name: "UpdateInvalidTodoURL" } },
  );
  check(invalidUpdateTodoResponse.response,
      { "Updating a non-existent todo is a 404": (res) => res.status === 404 }
  );

  // List todos again
  const secondListResponse = todoClient.listTodos(null, {
      tags: { name: "ListTodosURL" }
  });
  check(secondListResponse.response,
      { "Second list status is 200": (res) => res.status === 200 }
  );

  // Delete todo
  const deleteTodoResponse = todoClient.deleteTodo(
      todoId,
      { tags: { name: "DeleteTodoURL" } },
  );
  check(deleteTodoResponse.response,
      { "Delete todo status is 204": (res) => res.status === 204 }
  );

  // Delete a todo that doesn't exist
  const invalidDeleteTodoResponse = todoClient.deleteTodo(
      invalidTodoId,
      { tags: { name: "DeleteInvalidTodoURL" } },
  );
  check(invalidDeleteTodoResponse.response,
      { "Deleting a non-existent todo is a 404": (res) => res.status === 404 }
  );
}
