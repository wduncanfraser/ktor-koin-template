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

  // List todo lists up front
  const initialListResponse = todoClient.listTodoLists(
      null,
      { tags: { name: "ListTodoListsURL" } }
  );
  check(initialListResponse.response,
      { "Initial list status is 200": (res) => res.status === 200 }
  );

  // Create a todo list
  const createListResponse = todoClient.createTodoList(
      { name: "Load test list", description: "Created by k6" },
      { tags: { name: "CreateTodoListURL" } }
  );
  check(createListResponse.response,
      { "Create list status is 200": (res) => res.status === 200 }
  );
  const listId = createListResponse.data.id;

  // Get the todo list
  const getListResponse = todoClient.getTodoList(
      listId,
      { tags: { name: "GetTodoListURL" } }
  );
  check(getListResponse.response,
      { "Get list status is 200": (res) => res.status === 200 }
  );

  // Update the todo list
  const updateListResponse = todoClient.updateTodoList(
      listId,
      { name: "Updated list", description: null },
      { tags: { name: "UpdateTodoListURL" } }
  );
  check(updateListResponse.response,
      { "Update list status is 200": (res) => res.status === 200 }
  );

  // Get a todo list that doesn't exist
  const invalidListId = uuidv4();
  const invalidGetListResponse = todoClient.getTodoList(
      invalidListId,
      { tags: { name: "GetInvalidTodoListURL" } }
  );
  check(invalidGetListResponse.response,
      { "Getting a non-existent list is a 404": (res) => res.status === 404 }
  );

  // Create a todo in the list
  const createTodoResponse = todoClient.createTodoInList(
      listId,
      { name: "Cook dinner" },
      { tags: { name: "CreateTodoURL" } }
  );
  check(createTodoResponse.response,
      { "Create todo status is 200": (res) => res.status === 200 }
  );
  const todoId = createTodoResponse.data.id;

  // List todos in the list
  const listTodosInListResponse = todoClient.listTodosInList(
      listId,
      null,
      { tags: { name: "ListTodosInListURL" } }
  );
  check(listTodosInListResponse.response,
      { "List todos in list status is 200": (res) => res.status === 200 }
  );

  // Get the todo
  const getTodoResponse = todoClient.getTodoInList(
      listId,
      todoId,
      { tags: { name: "GetTodoURL" } }
  );
  check(getTodoResponse.response,
      { "Get todo status is 200": (res) => res.status === 200 }
  );

  // Update the todo (mark completed)
  const updateTodoResponse = todoClient.updateTodoInList(
      listId,
      todoId,
      { name: "Cook dinner", completed: true },
      { tags: { name: "UpdateTodoURL" } }
  );
  check(updateTodoResponse.response,
      { "Update todo status is 200": (res) => res.status === 200 }
  );

  // Update a todo that doesn't exist
  const invalidTodoId = uuidv4();
  const invalidUpdateTodoResponse = todoClient.updateTodoInList(
      listId,
      invalidTodoId,
      { name: "Cook dinner", completed: false },
      { tags: { name: "UpdateInvalidTodoURL" } }
  );
  check(invalidUpdateTodoResponse.response,
      { "Updating a non-existent todo is a 404": (res) => res.status === 404 }
  );

  // Get a todo that doesn't exist
  const invalidGetTodoResponse = todoClient.getTodoInList(
      listId,
      invalidTodoId,
      { tags: { name: "GetInvalidTodoURL" } }
  );
  check(invalidGetTodoResponse.response,
      { "Getting a non-existent todo is a 404": (res) => res.status === 404 }
  );

  // List all todos across lists
  const listAllTodosResponse = todoClient.listTodos(
      null,
      { tags: { name: "ListAllTodosURL" } }
  );
  check(listAllTodosResponse.response,
      { "List all todos status is 200": (res) => res.status === 200 }
  );

  // Delete the todo
  const deleteTodoResponse = todoClient.deleteTodoInList(
      listId,
      todoId,
      { tags: { name: "DeleteTodoURL" } }
  );
  check(deleteTodoResponse.response,
      { "Delete todo status is 204": (res) => res.status === 204 }
  );

  // Delete the todo list
  const deleteListResponse = todoClient.deleteTodoList(
      listId,
      { tags: { name: "DeleteTodoListURL" } }
  );
  check(deleteListResponse.response,
      { "Delete list status is 204": (res) => res.status === 204 }
  );

  // Delete a todo list that doesn't exist
  const invalidDeleteListResponse = todoClient.deleteTodoList(
      invalidListId,
      { tags: { name: "DeleteInvalidTodoListURL" } }
  );
  check(invalidDeleteListResponse.response,
      { "Deleting a non-existent list is a 404": (res) => res.status === 404 }
  );
}
