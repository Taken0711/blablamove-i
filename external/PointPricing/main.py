from flask import Flask
from flask_restful import Resource, Api, abort, reqparse

app = Flask(__name__)
api = Api(app)

USERS = {
    'Erick': {'points': 10},
    'Johann': {'points': 5},
    'Loic': {'points': 1}
}


def abort_if_user_doesnt_exist(user_name):
    if user_name not in USERS:
        abort(404, message="User {} doesn't exist".format(user_name))


parser = reqparse.RequestParser()
parser.add_argument('points')


class User(Resource):
    def get(self, user_name):
        abort_if_user_doesnt_exist(user_name)
        print(f"getting points of {user_name}:{USERS[user_name]}")
        return USERS[user_name], 200

    def put(self, user_name):
        args = parser.parse_args()
        abort_if_user_doesnt_exist(user_name)
        USERS[user_name]["points"] += int(args["points"])
        print(f"adding points to {user_name} {args['points']} final points {USERS[user_name]['points']}")
        return USERS[user_name]["points"], 201


class Users(Resource):
    def get(self):
        return USERS


api.add_resource(User, '/users/<string:user_name>')
api.add_resource(Users, '/users')

if __name__ == '__main__':
    app.run(debug=True, port=5000)
