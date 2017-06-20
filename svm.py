'''
Created on 7 juin 2016

@author: I326273
'''

import numpy as np
import time
from sklearn import cross_validation
from sklearn import svm
from sklearn import metrics
from sklearn.ensemble import RandomForestClassifier
from scipy.stats import randint as sp_randint
from scipy.stats import expon as sp_expon
from sklearn.grid_search import RandomizedSearchCV

# load data
csv_file = 'resultat.csv'
dataset = np.genfromtxt(csv_file, dtype=float, delimiter=',', skip_header=0)
weights_file = open('weights.txt', 'w')
output_file = open('output.txt', 'w')


svm_mean_accuracy_scores = []
rf_mean_accuracy_scores = []
svm_precision_scores = []
rf_precision_scores = []
svm_recall_scores = []
rf_recall_scores = []
	
X = dataset[:, 1:-1]
y = dataset[:,-1]
	
X_train, X_test, y_train, y_test = cross_validation.train_test_split(X, y, test_size=0.5, random_state=0)

# SVM classifier
print("SVM classifier : ")

# train and test the svm classifier to get a mean accuracy score
svm_clf = svm.LinearSVC(dual = False, penalty = 'l2', class_weight = 'balanced')

# specify possible hyperparameters
param_dist = {"C": sp_expon(scale=1)}

# run randomized search
n_iter_search = 20
svm_random_search = RandomizedSearchCV(svm_clf, param_distributions=param_dist, n_iter=n_iter_search)

start_time=time.time()
svm_random_search.fit(X_train, y_train)
print("RandomizedSearchCV took %.2f seconds for %d candidates parameter settings." % ((time.time() - start_time), n_iter_search))

best_C = svm_random_search.best_params_["C"]
print("Best value of C found on development set : ", best_C, " with score ", svm_random_search.best_score_)
print()

# evaluate by cross validation
svm_clf.set_params(C = best_C)
nb_folds = 6
start_time=time.time()
scores_acc = cross_validation.cross_val_score(svm_clf, X_test, y_test, cv = nb_folds, scoring = 'accuracy')
end_time=time.time()
svm_mean_accuracy_scores.append(np.mean(scores_acc))

svm_clf.fit(X_train, y_train)
pred = svm_clf.predict(X_test)
score_prec = metrics.precision_score(y_test, pred)
score_rec = metrics.recall_score(y_test, pred)
svm_precision_scores.append(score_prec)
svm_recall_scores.append(score_rec)


# train the svm classifier on all data to see the weight vector
new_svm_clf = svm.LinearSVC(dual = False, penalty = 'l2',C = best_C)
new_svm_clf.fit(X,y)

output_file.write("SVM classifier weights : ")
weights_file.write(np.array_str(new_svm_clf.coef_))
output_file.write(np.array_str(new_svm_clf.coef_))
output_file.write("\n")

print("Mean accuracy values for ", nb_folds, " tests : ", scores_acc)
print("Average accuracy : ", np.mean(scores_acc))
print("Precision : ", score_prec)
print("Recall : ", score_rec)
print("Weight vector : ", new_svm_clf.coef_)
print("Training time for ", nb_folds, "-fold cross-validation : ", end_time - start_time, " seconds")
print()


str_svm_acc = "Accuracy scores for SVM classifier : " + str(svm_mean_accuracy_scores) + "\n"
str_svm_prec = "Precision scores for SVM classifier : " + str(svm_precision_scores) + "\n"
str_svm_rec = "Recall scores for SVM classifier : " + str(svm_recall_scores) + "\n"
output_file.write("\n")
output_file.write(str_svm_acc)
output_file.write(str_svm_prec)
output_file.write(str_svm_rec)

weights_file.close()